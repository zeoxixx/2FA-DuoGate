<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

$host = 'localhost';
$user = 'root';
$pw = '0000';
$db_name = 'midas';
$conn = mysqli_connect($host, $user, $pw, $db_name);
$limit = 200;

// 전체 시도 기준
$total = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS total FROM firstauth"))['total'] ?? 0;

// 1차 인증 성공률
$firstauth_success = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS success FROM firstauth WHERE result = 1
"))['success'] ?? 0;
$firstauth_rate = $total > 0 ? ($firstauth_success / $total) * 100 : 0;

// 2차 인증 성공률
$secondauth_success = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS success FROM secondauth WHERE result = 1  AND time IS NOT NULL AND auth_time IS NOT NULL
"))['success'] ?? 0;
$secondauth_total = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS total FROM secondauth
	WHERE time IS NOT NULL AND auth_time IS NOT NULL
"))['total'] ?? 0;
$secondauth_rate = $secondauth_total > 0 ? ($secondauth_success / $secondauth_total) * 100 : 0;

// 도어 제어 성공률
$open_success = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS success FROM openrecord WHERE isOpened = 1
"))['success'] ?? 0;
$open_total = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) AS total FROM openrecord
"))['total'] ?? 0;
$open_rate = $open_total > 0 ? ($open_success / $open_total) * 100 : 0;

// 가중 평균 방식으로 최종 성공률 계산
$total_attempts = $total + $secondauth_total + $open_total;

$success_rate = $total_attempts > 0 ? round((
    ($firstauth_rate * $total) +
    ($secondauth_rate * $secondauth_total) +
    ($open_rate * $open_total)
) / $total_attempts, 1) : 0;

// 평균 시간 계산
$avg_time = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(AVG(
	TIMESTAMPDIFF(MICROSECOND, f.req_time_server, f.time) +
    	TIMESTAMPDIFF(MICROSECOND, s.time, s.auth_time) +
    	TIMESTAMPDIFF(MICROSECOND, s.auth_time, o.time)
  	) / 1000000, 3) AS total_avg_time
  	FROM openrecord o
  	JOIN secondauth s ON o.idSecondAuth = s.idSecondAuth
  	JOIN firstauth f ON s.idFirstAuth = f.idFirstAuth
  	WHERE f.result = 1 AND s.result = 1 AND o.isOpened = 1
    	AND f.req_time_server IS NOT NULL AND f.time IS NOT NULL
    	AND s.time IS NOT NULL AND s.auth_time IS NOT NULL
    	AND o.time IS NOT NULL
"))['total_avg_time'] ?? 0;

//지연률 계산
$delay_rate = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(SUM(CASE 
		WHEN TIMESTAMPDIFF(SECOND, f.req_time_server, f.time) > 2
			OR TIMESTAMPDIFF(SECOND, s.time, s.auth_time) > 15
			OR TIMESTAMPDIFF(SECOND, s.auth_time, o.time) >2
		THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS delay_rate
	FROM openrecord o
	JOIN secondauth s ON o.idSecondAuth = s.idSecondAuth
	JOIN firstauth f ON s.idFirstAuth = f.idFirstAuth
	WHERE o.isOpened = 1 AND s.result = 1 AND f.result = 1
"))['delay_rate'] ?? 0;

// 표 데이터 불러오기
$query = "
	SELECT u.idApp, u.name, f.macAddrFromApp, s.input,
	f.timeFromApp AS start_time, o.time AS end_time
	FROM openrecord o
	JOIN secondauth s ON o.idSecondAuth = s.idSecondAuth
	JOIN firstauth f ON s.idFirstAuth = f.idFirstAuth
	JOIN users u ON f.idUser = u.idUser
	WHERE s.result = 1 AND f.result = 1 AND o.isOpened = 1
	ORDER BY o.time DESC
	LIMIT $limit ";
$result = mysqli_query($conn, $query);

$rows = '';
$index = 1;

while ($row = mysqli_fetch_assoc($result)) {
	$rows .= "<tr>
	<td>{$index}</td>
	<td>{$row['idApp']}</td>
	<td>{$row['name']}</td>
	<td>{$row['macAddrFromApp']}</td>
	<td>{$row['input']}</td>
	<td>{$row['start_time']}</td>
	<td>{$row['end_time']}</td>
	</tr>";
	$index++;
}

$response = [
	'circle_stats' => [
	'success_rate' => $success_rate,
	'avg_time' => $avg_time,
	'delay_rate' => $delay_rate
	],
	'table_data' => $rows
];

header('Content-Type: application/json');
echo json_encode($response);
mysqli_close($conn);
?>