<?php
define('VERSION', "v.1.3");
// name of the database file (may also be a path)
define('DB_NAME', "dayon.db");
// minimal length of the tokens to be generated (33^N-1 variants)
define('TOKEN_MIN_LENGTH', 4);
// number of seconds after which the token will be purged
define('TOKEN_LIFETIME', 604800);
// maximum number of tokens that can be generated for a single IP
define('TOKEN_LIMIT', 700);
// 8<---8<---8<---
header('Content-type: text/plain');
if (isset($_GET['port'])) {
    $port = clean($_GET['port'], 6);
    // param missing = legacy mode (assistant never closed)
    $closed = isset($_GET['closed']) ? clean($_GET['closed'], 2) : 0;
    if (isValidPort($port)) {
        $pdo = new PDO('sqlite:'.DB_NAME);
        createDatabase($pdo);
        echo createToken($pdo, $port, $closed),"\n";
        if (rand(0, 5) == 5) {
            removeOldTokens($pdo);
        }
    }
} else if (isset($_GET['token'])) {
    $token = clean($_GET['token'], TOKEN_MIN_LENGTH * 2);
    $pdo = new PDO('sqlite:'.DB_NAME);
    // param missing = legacy mode (assisted never open)
    if (!isset($_GET['v'])) {
        echo readBasicToken($pdo, $token),"\n";
        updateAssisted($pdo, $token, 0, $_SERVER['REMOTE_ADDR']);
    } else {
        if (isset($_GET['closed'])) {
            $closed = isset($_GET['closed']) ? clean($_GET['closed'], 2) : 0;
            updateAssistant($pdo, $token, $closed);
        } else {
            $open = isset($_GET['open']) ? clean($_GET['open'], 2) : 0;
            updateAssisted($pdo, $token, $open, $_SERVER['REMOTE_ADDR']);
        }
        echo readToken($pdo, $token),"\n";
    }
} else {
    echo VERSION,"\n";
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

function createToken($pdo, $port, $closed) {
    if (checkAvailable($pdo, $_SERVER['REMOTE_ADDR']) <= 0) {
       return substr(str_shuffle("ABCDEFGHJKLMNPQRSTUVWXYZ123456789"), 0, rand(4, 12))."\n";
    }
    $token = computeToken(TOKEN_MIN_LENGTH);
    $attempt = 0;
    while (!insertToken($pdo, $token, $_SERVER['REMOTE_ADDR'], $port, $closed) && $attempt < 10) {
        $length = $attempt < TOKEN_MIN_LENGTH ? TOKEN_MIN_LENGTH : round(TOKEN_MIN_LENGTH+$attempt/2);
        $token = computeToken($length);
        $attempt++;
    }
    return $token;
}

function checkAvailable($pdo, $address) {
    $sql = "SELECT COUNT(*) FROM tokens WHERE assistant = :address";
    $stmt = $pdo->prepare($sql);
    $stmt->bindParam(':address', $address, PDO::PARAM_STR);
    $stmt->execute();
    $stmt->bindColumn(1, $count);
    $stmt->fetch(PDO::FETCH_BOUND);
    return TOKEN_LIMIT - $count;
}

function computeToken($length) {
    $token = substr(str_shuffle("ABCDEFGHJKLMNPQRSTUVWXYZ123456789"), 0, $length-1);
    $token .= strtoupper(substr(sha1($token), -1));
    return $token;
}

function insertToken($pdo, $token, $address, $port, $closed) {
    $sql = "INSERT INTO tokens (token,assistant,port,ts,closed) VALUES (:token,:address,:port,:ts,:closed)";
    $ts = time();
    // assistant 0 not closed, 1 closed -> ts
    $closed = $closed == 1 ? $ts : 0;
    $stmt = $pdo->prepare($sql);
    $stmt->bindParam(':token', $token, PDO::PARAM_STR, 7);
    $stmt->bindParam(':address', $address, PDO::PARAM_STR);
    $stmt->bindParam(':port', $port, PDO::PARAM_INT);
    $stmt->bindParam(':ts', $ts, PDO::PARAM_INT);
    $stmt->bindParam(':closed', $closed, PDO::PARAM_INT);
    $success = $stmt->execute();
    if (!$success) {
        // print_r($stmt->errorInfo());
        return 0;
    } else {
        return 1;
    }
}

function removeOldTokens($pdo) {
    $ts = time();
    $delete = "DELETE FROM tokens WHERE ts < ?";
    $stmt = $pdo->prepare($delete);
    $stmt->execute(array($ts-TOKEN_LIFETIME));
}

function readBasicToken($pdo, $token) {
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

function readToken($pdo, $token) {
    $sql = "SELECT assistant,port,assisted,closed,open FROM tokens WHERE token = :token";
    $stmt = $pdo->prepare($sql);
    if ($stmt->execute([":token" => $token])) {
        $stmt->bindColumn(1, $assistant);
        $stmt->bindColumn(2, $port);
        $stmt->bindColumn(3, $assisted);
        $stmt->bindColumn(4, $closed);
        $stmt->bindColumn(5, $open);
        return $stmt->fetch(PDO::FETCH_BOUND) ? "$assistant*$port*$assisted*$closed*$open" : "";
    } else {
        return "";
    }
}

function updateAssisted($pdo, $token, $open, $address) {
    $sql = "UPDATE tokens SET assisted = :address,open = :open,ts = :ts WHERE token = :token";
    $ts = time();
    // assisted -1 unknown, 0 not open, 1 open -> ts
    $open = $open == 1 ? $ts : $open;
    $stmt = $pdo->prepare($sql);
    $stmt->bindParam(':address', $address, PDO::PARAM_STR);
    $stmt->bindParam(':open', $open, PDO::PARAM_INT);
    $stmt->bindParam(':ts', $ts, PDO::PARAM_INT);
    $stmt->bindParam(':token', $token, PDO::PARAM_STR, 7);
    $stmt->execute();
}

function updateAssistant($pdo, $token, $closed) {
    $sql = "UPDATE tokens SET closed = :closed,ts = :ts WHERE token = :token";
    $ts = time();
    $closed = $closed == 0 ? 0 : $ts;
    $stmt = $pdo->prepare($sql);
    $stmt->bindParam(':closed', $closed, PDO::PARAM_INT);
    $stmt->bindParam(':ts', $ts, PDO::PARAM_INT);
    $stmt->bindParam(':token', $token, PDO::PARAM_STR, 7);
    $stmt->execute();
}

function createDatabase($pdo) {
    $sql = "CREATE TABLE IF NOT EXISTS `tokens` (`token` TEXT,`assistant` TEXT,`port` INTEGER,`assisted` TEXT,`closed` INTEGER,`open` INTEGER,`ts` INTEGER, PRIMARY KEY(`token`))";
    $stmt = $pdo->prepare($sql);
    $stmt->execute();
    // print_r($stmt->errorInfo());
}
?>