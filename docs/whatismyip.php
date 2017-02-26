<?php

header('Content-type: text/plain');

if (isset($_SERVER['HTTP_X_REMOTE_ADDR'])) {
  printf("%s\n", $_SERVER['HTTP_X_REMOTE_ADDR']);
} else {
  printf("%s\n", $_SERVER['REMOTE_ADDR']);
}

?>