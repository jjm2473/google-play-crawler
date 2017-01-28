<?php

if ($argc != 4) {
    echo 'Usage: ' . __FILE__ . ' conf_file package_name destination_data_dir' . PHP_EOL;
    echo 'Eg: php download.php ~/Desktop/crawler.conf com.tencent.mtt.intl ~/Desktop' . PHP_EOL;
    return;
}

$conf = $argv[1];
$package = $argv[2];
$data_dir = $argv[3];
$cmd = `java -jar target/googleplaycrawler-0.3-jar-with-dependencies.jar -f $conf download 21 $package | grep curl`;
$cmd = trim($cmd) . " | grep Location";
$cmd = `$cmd`;
$cmd = explode("Location: ", $cmd);
$cmd = "curl \"" . trim($cmd[1]) . "\" > " . $data_dir . "/" . $package . ".apk";
`$cmd`;