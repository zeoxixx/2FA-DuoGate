<?php
$host = 'localhost';
$user = 'root';
$pw = '0000';
$db_name = 'midas';
$conn = mysqli_connect($host, $user, $pw, $db_name);
$limit = 200;

$total = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) as total FROM firstauth
	"))['total'] ?? 0;
$success = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT COUNT(*) as success FROM firstauth WHERE result = 1
	"))['success'] ?? 0;
$avg_time = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, req_time_server, time)) / 1000000, 3) as avg_time
	FROM firstauth
	WHERE result = 1
	"))['avg_time'] ?? 0;
$delay = mysqli_fetch_assoc(mysqli_query($conn, "
	SELECT ROUND(SUM(TIMESTAMPDIFF(SECOND, req_time_server, time) > 2) / COUNT(*) * 100, 1) as delay_rate
	FROM firstauth
	"))['delay_rate'] ?? 0;


$query = "
	SELECT u.idApp, u.name,
		f.result, f.token, f.macAddrFromApp,
		f.req_time_server AS start_time, 
		f.time AS end_time
	FROM firstauth f
	JOIN users u on f.idUser = u.idUser
	ORDER BY f.timeFromApp DESC
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
	<td>{$status}</td>
	<td>{$row['token']}</td>
	<td>{$row['macAddrFromApp']}</td>
	<td>{$row['start_time']}</td>
	<td>{$row['end_time']}</td>
	</tr>";
	$index++;
}

$response = [
	'circle_stats' => [
	'success_rate' => $total > 0? round($success / $total * 100, 1) : 0,
	'avg_time' => $avg_time,
	'delay_rate' => $delay
	],
	'table_data' => $rows
];

header('Content-Type: application/json');
echo json_encode($response);
mysqli_close($conn);
?>