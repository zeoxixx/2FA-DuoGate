<?php
$host = 'localhost';
$user = 'root';
$pw = '0000';
$db_name = 'midas';
$conn = mysqli_connect($host, $user, $pw, $db_name);
$limit = 200;

// 전체 2차 인증 시도 횟수
$total = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS total FROM secondauth
	WHERE time IS NOT NULL AND auth_time IS NOT NULL
"))['total'] ?? 0;

// 성공한 2차 인증
$success = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS success FROM secondauth WHERE result = 1 AND time IS NOT NULL AND auth_time IS NOT NULL
"))['success'] ?? 0;

// 평균 인증 시간 (초)
$avg_time = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, time, auth_time) / 1000000), 3) AS avg_time
	FROM secondauth WHERE result = 1 AND time IS NOT NULL AND auth_time IS NOT NULL
"))['avg_time'] ?? 0;

// 지연률 (15초 이상 소요된 경우)
$delay_rate = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(SUM(CASE WHEN TIMESTAMPDIFF(SECOND, time, auth_time) > 15 THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS delay_rate
	FROM secondauth
	WHERE result = 1 AND time IS NOT NULL AND auth_time IS NOT NULL
"))['delay_rate'] ?? 0;


$query = "
	SELECT u.idApp, u.name, s.input, s.result, s.time, s.auth_time, s.fail_num
	FROM secondauth s
	JOIN firstauth f ON s.idFirstAuth = f.idFirstAuth
	JOIN users u ON f.idUser = u.idUser
	WHERE s.auth_time IS NOT NULL
	ORDER BY s.time DESC
	LIMIT $limit ";
$result = mysqli_query($conn, $query);
$rows = '';
$index = 1;

while ($row = mysqli_fetch_assoc($result)) {
	$status = $row['result'] == 1 ? '성공' : '실패';
	$rows .= "<tr>
	<td>{$index}</td>
	<td>{$row['idApp']}</td>
	<td>{$row['name']}</td>
	<td>{$row['input']}</td>
	<td>{$status}</td>
	<td>{$row['time']}</td>
	<td>{$row['auth_time']}</td>
	<td>{$row['fail_num']}</td>
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