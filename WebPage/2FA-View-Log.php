
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<title>성능요약&로그</title>

<style>
	*{
	margin: 0;
	padding: 0;
	box-sizing: border-box;
	font-family: Noto Sans or Pretendard;
	}
	body {
	background-color : #E6F2FF;
	}

	header {
	background-color : #021024;
	color: white;
	padding: 20px;
	font-size: 24px;
	text-align: right;
	font-weight: bold;
	border-bottom: 2px solid #021024;
	}

	.container {
	display: flex;
	height: calc(100vh - 70px);
	}

	.sidebar {
	width: 180px;
	background-color: #2F3E59;
	padding: 20px 10px;
	}
	.sidebar button {
	width: 100%;
	background-color: #607A9B;
	color: white;
	padding: 14px;
	margin-bottom: 10px;
	border: none;
	border-radius: 6px;
	cursor: pointer;
	font-size: 16px;
	box-shadow: 0 2px 4px rgba(0,0,0,0.2);
	}
	.sidebar button:hover {
	background-color: #4F6787;
	}

	.content {
	flex: 1;
	padding: 40px;
	background-color: #E6F2FF;
	overflow-y: auto;
	}

	section {
	display: none;
	margin-bottom: 50px;
	background-color: white;
	border: 1px solid #d0e2ff;
	padding: 30px;
	border-radius: 8px;
	box-shadow: 0 4px 10px rgba(0,0,0,0.05);
	}
	section.active {
	display: block;
	}

	h2{
	margin-bottom: 20px;
	color: #021024;
	font-size: 20px;
	}
	table {
	width: 100%;
	border-collapse: collapse;
	background-color: white;
	}

	th, td {
	border: 1px solid #ccc;
	padding: 10px;
	text-align: center;
	}

	th {
	background-color: #32455D;
	color: white;
	}
	
	.circle-stats {
	display: flex;
	flex-wrap: wrap;
	gap: 50px;
	margin-bottom: 30px;
	justify-content: center;
	}

	.circle {
	width: 120px;
	height: 120px;
	border-radius: 50%;
	background-color: #C9E4F2;
	color: #021024;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	font-size: 22px;
	font-weight: bold;
	box-shadow: 0 2px 6px rgba(0,0,0,0.2);
	transition: 0.3s ease;
	}

	.circle small {
	font-size: 14px;
	font-weight: normal;
	margin-bottom : 4px;
	}
</style>
</head>

<body>
	<header>2 Factor Access System</header>
	<div class="container">
		<nav class="sidebar">
		<button onclick="showSection('s1')">전체 인증</button>
		<button onclick="showSection('s2')">1차 인증</button>
		<button onclick="showSection('s3')">2차 인증</button>
		<button onclick="showSection('s4')">도어 제어</button>
		</nav>
	<div class="content">
	<section id="s1" class="active">
	<h2>전체 인증 성능</h2>
		<div class="circle-stats" id="circle-s1">

		</div>
	
		<table>
		<thead>
		<tr>
		<th style="width: 3%";></th>
		<th style="width: 12%";>ID</th>
		<th style="width: 14%";>이름</th>
		<th style="width: 24%";>SSAID</th>
		<th style="width: 15%";>2차 입력값</th>
		<th style="width: 16%";>인증 시작 시각</th>
		<th style="width: 16%";>인증 완료 시각</th>
		</tr>
		</thead>
		<tbody>

		</tbody>
		</table>
	</section>

	<section id="s2">
	<h2>1차 인증 성능</h2>
		<div class="circle-stats" id="circle-s2">
		<div class="circle">3<br><small>성공률</small></div>
		<div class="circle">3s<br><small>평균 시간</small></div>
		<div class="circle">1s<br><small>평균 지연</small></div>
		</div>
		
		<table>
		<thead>
		<tr>
		<th style="width: 3%";></th>
		<th style="width: 10%";>ID</th>
		<th style="width: 10%";>이름</th>
		<th style="width: 9%";>성공 여부</th>
		<th style="width: 23%";>생성 토큰</th>
		<th style="width: 15%";>SSAID</th>
		<th style="width: 15%";>보낸 시각</th>
		<th style="width: 15%";>완료 시각</th>
		</tr>
		</thead>
		<tbody>

		</tbody>
		</table>

	</section>

	<section id="s3">
	<h2>2차 인증 성능</h2>
		<div class="circle-stats" id="circle-s3">
		<div class="circle">$<br><small>성공률</small></div>
		<div class="circle">s<br><small>평균 시간</small></div>
		<div class="circle">%<br><small>평균 지연</small></div>
		</div>
		
		<table>
		<thead>
		<tr>
		<th style="width: 3%";></th>
		<th style="width: 12%";>ID</th>
		<th style="width: 13%";>이름</th>
		<th style="width: 14%";>입력된 OTP</th>
		<th style="width: 10%";>성공 여부</th>
		<th style="width: 20%";>OTP 발급 시간</th>
		<th style="width: 20%";>인증 완료 시간</th>
		<th style="width: 8%";>누적 실패</th>
		</tr>
		</thead>
		<tbody>
		
		</tbody>
		</table>

	</section>

	<section id="s4">
	<h2>도어 제어 성능</h2>
		<div class="circle-stats" id="circle-s4">
		<div class="circle">%<br><small>성공률</small></div>
		<div class="circle">s<br><small>평균 시간</small></div>
		<div class="circle">s<br><small>평균 지연</small></div>
		</div>
		
		<table>
		<thead>
		<tr>
		<th style="width: 3%";></th>
		<th style="width: 13%";>출입자ID</th>
		<th style="width: 18%";>출입자이름</th>
		<th style="width: 16%";>도어락ID</th>
		<th style="width: 15%";>오픈여부</th>
		<th style="width: 15%";>오픈시도횟수</th>
		<th style="width: 20%";>시간</th>
		</tr>
		</thead>
		<tbody>
		
		</tbody>
		</table>

	</section>
	</div>
	</div>

<script>
	function showSection(id) {
		document.querySelectorAll('section').forEach(s => s.classList.remove('active'));
		document.getElementById(id).classList.add('active');

		switch(id) {
		case 's1' : updateTable('s1', 'get_s1T.php'); break;
		case 's2' : updateTable('s2', 'get_s2T.php'); break;
		case 's3' : updateTable('s3', 'get_s3T.php'); break;
		case 's4' : updateTable('s4', 'get_s4T.php'); break;
		}
	}

	function updateTable(sectionId, url) {
	fetch(url)
	.then(res=> res.json())
	.then(data=> {
	const tbody = document.querySelector(`#${sectionId} tbody`);
	if (tbody) {
		tbody.innerHTML = data.table_data || `<tr><td colspan="${sectionId === 's1' ? 7 : sectionId === 's2' ? 8 : sectionId === 's3' ? 8 : 7}">데이터 없음</td></tr>`;
	}

	// 모든 섹션의 circle-stats 업데이트
            const circleStats = document.querySelector(`#circle-${sectionId}`);
            if (circleStats && data.circle_stats) {
                circleStats.innerHTML = `
                    <div class="circle">${data.circle_stats.success_rate || 0}%<br><small>성공률</small></div>
                    <div class="circle">${data.circle_stats.avg_time || 0}s<br><small>평균 시간</small></div>
                    <div class="circle">${data.circle_stats.delay_rate || 0}%<br><small>지연률</small></div>
                `;
            }
        })
        .catch(error => {
            console.error('Error fetching data:', error);
            const tbody = document.querySelector(`#${sectionId} tbody`);
            if (tbody) {
                tbody.innerHTML = `<tr><td colspan="${sectionId === 's1' ? 7 : sectionId === 's2' ? 8 : sectionId === 's3' ? 8 : 7}">오류 발생</td></tr>`;
            }
        });
}


	function autoRefreshCurrentSection() {
	const activeSection = document.querySelector('section.active');
	if (!activeSection) return;

	switch (activeSection.id) {
	case 's1' :
		updateTable('s1', 'get_s1T.php');
		break;
	case 's2' :
		updateTable('s2', 'get_s2T.php');
		break;
	case 's3' :
		updateTable('s3', 'get_s3T.php');
		break;
	case 's4' :
		updateTable('s4', 'get_s4T.php');
		break;
	}
}

window.onload = function () {
	const url = new URL(window.location.href);
	
	if (url.searchParams.has("s2page")) showSection("s2");
	else if (url.searchParams.has("s3page")) showSection("s3");
	else if (url.searchParams.has("s4page")) showSection("s4");
	else showSection("s1");
	
	if (window.history.replaceState) {
	window.history.replaceState({}, document.title, window.location.pathname);
	}

	autoRefreshCurrentSection();
	setInterval(autoRefreshCurrentSection, 30000); // 30초마다 갱신
};
</script>
</body>
</html>










