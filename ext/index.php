<?php
define('DB_NAME', "dayon.db");
define('TOKEN_LIFETIME', 604800000);
header('Content-type: text/plain');
if (isset($_GET['port'])) {
	$port = clean($_GET['port'], 5);
    $token = substr(str_shuffle("ABCDEFGHJKLMNPQRSTUVWXYZ123456789"), 0, 6);
	$token .= checksum($token);
	$pdo = new PDO('sqlite:'.DB_NAME);
	echo insertToken(strtoupper($token), $_SERVER['REMOTE_ADDR'], $port, $pdo);
	if (rand(0, 5) == 5) {
		removeOldTokens($pdo);
	}
}

if (isset($_GET['token'])) {
	$token = clean($_GET['token'], 7);
	$pdo = new PDO('sqlite:'.DB_NAME);
	echo readToken($token, $pdo);
	updateToken($token, $_SERVER['REMOTE_ADDR'], $pdo);
}

function clean($val, $maxLen = "") {
	$val = trim(strip_tags($val));
	if (!empty($maxLen)) {
		$val = substr($val, 0, $maxLen);
	}
	return $val;
}

function checksum($in) {
        return substr(sha1($in), -1);
}

function insertToken($token, $address, $port, $pdo) {
    $sql = "INSERT INTO tokens (token,assistant,port,ts) VALUES (:token,:address,:port,:ts)";
	$date = new DateTime();
	$ts = $date->getTimestamp();
        $stmt = $pdo->prepare($sql);
        $stmt->bindParam(':token', $token, PDO::PARAM_STR, 7);
        $stmt->bindParam(':address', $address, PDO::PARAM_STR);
        $stmt->bindParam(':port', $port, PDO::PARAM_INT);
        $stmt->bindParam(':ts', $ts, PDO::PARAM_INT);
        $success = $stmt->execute();
        if (!$success) {
 		print_r($stmt->errorInfo());
	} else {
		return $token;
	}
}

function removeOldTokens($pdo) {
	$date = new DateTime();
	$ts = $date->getTimestamp();
	$delete = "DELETE FROM tokens WHERE ts < ?";
	$stmt = $pdo->prepare($delete);
	$stmt->execute(array($ts-TOKEN_LIFETIME));
}

function readToken($token, $pdo) {
	$sql = "SELECT assistant,port FROM tokens WHERE token = :token";
    $stmt = $pdo->prepare($sql);
	if ($stmt->execute([":token" => $token])) {
	    $stmt->bindColumn(1, $address);
	    $stmt->bindColumn(2, $port);
	    return $stmt->fetch(PDO::FETCH_BOUND) ? "$address*$port" : "";
	} else {
	    return "";
	}
}

function updateToken($token, $address, $pdo) {
	$sql = "UPDATE tokens SET assisted = :address,ts = :ts WHERE token = :token";
	$date = new DateTime();
	$ts = $date->getTimestamp();
    $stmt = $pdo->prepare($sql);
	$stmt->bindParam(':address', $address, PDO::PARAM_STR);
	$stmt->bindParam(':ts', $ts, PDO::PARAM_INT);
	$stmt->bindParam(':token', $token, PDO::PARAM_STR, 7);
	$stmt->execute();
}
?>
