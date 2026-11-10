<?php
session_start();

if (isset($_SESSION["logged_in"])) {
	header("Location: 2FA-View-Log.php");
	exit();
}

$error_message = '';

if ($_SERVER["REQUEST_METHOD"] === "POST") {
	$admin_id = $_POST['admin_id'] ?? '';
	$admin_pw = $_POST['admin_pw'] ?? '';

    	$id = 'midas';
    	$pw = '1234';

    	if ($admin_id === $id && $admin_pw === $pw) {
        		$_SESSION["logged_in"] = true;
        		header("Location: 2FA-View-Log.php");
        		exit();
    	} else {
        		$error_message = "아이디 혹은 비밀번호가 일치하지 않습니다.";
    	}
}
?>

<!DOCTYPE html>
<html lang ="ko">
<head>
	<meta charset="UTF-8">
	<title>관리자 로그인</title>
	<style>
		body{ /*전체 페이지 디자인*/
			margin: 0;
			font-family: Noto Sans or Pretendard;
			background-color : #E6F2FF;
		}

		.header-bar {
			background-color: #021024;
			color: white;
			padding: 15px;
			text-align: right;
			font-size: 20px;
			font-weight: bold;
			border-bottom: 2px solid #021024;
		}
		.login-container {
			display: flex;
			flex-direction : column;
			align-items: center;
			margin-top: 100px;
		}
		.login-box {
			background-color: white;
			padding: 30px;
			border-radius: 8px;
			box-shadow: 0px 4px 12px rgba(2, 16, 36, 0.2);
			width: 300px;
		}
		.login-box h2{
			text-align: center;
			margin-bottom: 20px;
			color: #021024; 
		}

		label {
			font-size: 14px;
			color: #052659;
		}
		input[type="text"], input[type="password"] {
			width: 100%;
			padding: 10px;
			margin: 8px 0 15px 0;
			box-sizing: border-box;
			border: 1px solid #5483B3;
			border-radius: 6px;
			background-color: #F7FBFF;
		}

		button {
			width: 100%;
			padding: 10px;
			background-color: #021024;
			color: white;
			border: none;
			font-size: 16px;
			cursor: pointer;
			border-radius: 4px;
			box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
		}
		button:hover {
			background-color: #1B2A3E;
		}

		.notice {
			text-align: center;
			margin-top: 15px;
			font-size: 14px;
			color: #333;
		}
	</style>
</head>


<body>

<div class="header-bar">
	<span>2 Factor Access System</span>
</div>

<div class="login-container">
	<div class="login-box">
		<h2>LOGIN</h2>
		<?php if (!empty($error_message)) echo "<p style='color:red; text-align:center;'>$error_message</p>"; ?>
		
		<form method="POST">
			<label for="admin_id">아이디</label>
			<input type="text" name="admin_id" id="admin_id" required>

			<label for="admin_pw">비밀번호</label>
			<input type="password" name="admin_pw" id="admin_pw" required>

			<button type="submit">GO</button>
		</form>
	</div>
	<div class = "notice">관리자 전용 페이지입니다.</div>
</div>
</body>
</html>



