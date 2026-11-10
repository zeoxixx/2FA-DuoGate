<?php
$host = 'localhost';
$user = 'root';
$pw = '0000';
$db_name = 'midas';
$conn = mysqli_connect($host, $user, $pw, $db_name);
$limit = 200;

// 전체 도어 제어 시도 횟수
$total = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS total FROM openrecord
"))['total'] ?? 0;

// 도어 오픈 성공 횟수
$success = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS success FROM openrecord WHERE isOpened = 1
"))['success'] ?? 0;

// 평균 도어 오픈 시간
$avg_time = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, s.auth_time, o.time) / 1000000), 3) AS avg_time
	FROM openrecord o
	JOIN secondauth s ON o.idSecondAuth = s.idSecondAuth
	WHERE o.isOpened = 1 AND s.auth_time IS NOT NULL AND o.time IS NOT NULL
"))['avg_time'] ?? 0;

// 지연률 (2초 이상 소요)
$delay_rate = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(SUM(CASE WHEN TIMESTAMPDIFF(SECOND, s.auth_time, o.time) > 3 THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS delay_rate
	FROM openrecord o
	JOIN secondauth s ON o.idSecondAuth = s.idSecondAuth
	WHERE o.isOpened = 1 AND s.auth_time IS NOT NULL AND o.time IS NOT NULL
"))['delay_rate'] ?? 0;

$query = "
	SELECT u.idApp, u.name, d.ssid AS doorlock,
	o.isOpened, o.n, o.time
	FROM openrecord o
	JOIN secondauth s ON o.idSecondAuth = s.idSecondAuth
	JOIN firstauth f ON s.idFirstAuth = f.idFirstAuth
	JOIN users u ON f.idUser = u.idUser
	JOIN devices d ON d.idDevice = o.idDevice
	ORDER BY o.time DESC
	LIMIT $limit ";
$result = mysqli_query($conn, $query);
$rows = '';
$index = 1;

while ($row = mysqli_fetch_assoc($result)) {
	$opened = $row['isOpened'] == 1 ? '열림' : '실패';
	$rows .= "<tr>
	<td>{$index}</td>
	<td>{$row['idApp']}</td>
	<td>{$row['name']}</td>
	<td>{$row['doorlock']}</td>
	<td>{$opened}</td>
	<td>{$row['n']}</td>
	<td>{$row['time']}</td>
	</tr>";
	$index++;
}

$response = [
	'circle_stats' => [
	'success_rate' => $total > 0 ? round($success / $total * 100, 1) : 0,
	'avg_time' => $avg_time,
	'delay_rate' => $delay_rate
	],
	'table_data' => $rows
];

header('Content-Type: application/json');
echo json_encode($response);
mysqli_close($conn);
?>