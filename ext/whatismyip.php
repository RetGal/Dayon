<?php
header('Content-type: text/plain');
if (!empty($_REQUEST['p'])) {
    $timeout = 1;
    $address = $_SERVER['REMOTE_ADDR'];
    $port = substr($_REQUEST['p'], 0, 5);
    $errno = 0; $errstr = '';
    // Handle IPv6 literals by wrapping in brackets for the URI
    $target = filter_var($address, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6) ? "tcp://[{$address}]:{$port}" : "tcp://{$address}:{$port}";
    $fp = @stream_socket_client($target, $errno, $errstr, $timeout);
    if ($fp) {
        fclose($fp);
        echo 1;
    } else {
        echo 0;
    }
} else {
    echo $_SERVER['REMOTE_ADDR'];
}
?>