<?php
define('DB_NAME', "dayon.db");
define('TOKEN_MIN_LENGTH', 4);
define('TOKEN_LIFETIME', 604800000);
header('Content-type: text/plain');
if (isset($_GET['port'])) {
    $port = clean($_GET['port'], 6);
    if (isValidPort($port)) {
        $pdo = new PDO('sqlite:'.DB_NAME);
        createDatabase($pdo);
        echo createToken($pdo, $port),"\n";
        if (rand(0, 5) == 5) {
            removeOldTokens($pdo);
        }
    }
} else if (isset($_GET['token'])) {
    $token = clean($_GET['token'], 7);
    $pdo = new PDO('sqlite:'.DB_NAME);
    echo readToken($token, $pdo),"\n";
    updateToken($token, $_SERVER['REMOTE_ADDR'], $pdo);
}

function clean($val, $maxLen = "") {
    $val = trim(strip_tags($val));
    if (!empty($maxLen)) {
        $val = substr($val, 0, $maxLen);
    }
    return $val;
}

function isValidPort($port) {
    return is_numeric($port) && $port > 0 && $port < 65536;
}

function createToken($pdo, $port) {
    $token = computeToken(TOKEN_MIN_LENGTH);
    $attempt = 0;
    while (!insertToken($token, $_SERVER['REMOTE_ADDR'], $port, $pdo) && $attempt < 10) {
        $length = $attempt < TOKEN_MIN_LENGTH ? TOKEN_MIN_LENGTH : round(TOKEN_MIN_LENGTH+$attempt/2);
        $token = computeToken($length);
        $attempt++;
    }
    return $token;
}

function computeToken($length) {
    $token = substr(str_shuffle("ABCDEFGHJKLMNPQRSTUVWXYZ123456789"), 0, $length-1);
    $token .= strtoupper(substr(sha1($token), -1));
    return $token;
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
        // print_r($stmt->errorInfo());
        return 0;
    } else {
        return 1;
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

function createDatabase($pdo) {
    $sql = "CREATE TABLE IF NOT EXISTS `tokens` (`token` TEXT,`assistant` TEXT,`port` INTEGER,`assisted` TEXT,`ts` INTEGER, PRIMARY KEY(`token`))";
    $stmt = $pdo->prepare($sql);
    $stmt->execute();
    // print_r($stmt->errorInfo());
}
?>
