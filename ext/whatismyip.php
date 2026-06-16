<?php
header('Content-type: text/plain');
if (!empty($_REQUEST['p'])) {
    set_time_limit(1);
    $address = $_SERVER['REMOTE_ADDR'];
    $type = filter_var($address, FILTER_VALIDATE_IP, FILTER_FLAG_IPV4) ? AF_INET : AF_INET6;
    $port = substr($_REQUEST['p'], 0, 5);
    $socket=socket_create($type, SOCK_STREAM, SOL_TCP);
    if ($socket &&
        socket_set_option($socket, SOL_SOCKET, SO_SNDTIMEO, array('sec'=>0, 'usec'=>500)) &&
        socket_set_option($socket, SOL_SOCKET, SO_RCVTIMEO, array('sec'=>0, 'usec'=>500)) &&
        socket_connect($socket, $address, $port)) {
        echo 1;
    } else {
        echo 0;
    }
} else {
    echo $_SERVER['REMOTE_ADDR'];
}
?>