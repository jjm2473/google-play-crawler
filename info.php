<?php

if ($argc != 2) {
    echo 'Usage: ' . __FILE__ . ' conf_file' . PHP_EOL;
    echo 'Eg: php info.php ~/Desktop/crawler.conf' . PHP_EOL;
    return;
}

/**
 * 根据线上的数据下载gp上对应的包的信息
 *
 * @param $conf 配置文件
 * @param $source 源文件
 * @param $destination 目标文件
 */
function download_gp($conf, $source, $destination)
{
    $handler = fopen($source, "rb");
    while (!feof($handler)) {
        $line = trim(fgets($handler));
        if (empty($line)) {
            continue;
        }
        list($pkg, $vcode, $sha1) = explode(" | ", $line);
        $data = `java -jar ./info.jar -f $conf download 21 $pkg`;
        file_put_contents($destination, $data . PHP_EOL, FILE_APPEND | LOCK_EX);
        sleep(3);
    }
    fclose($handler);
}

/**
 * 根据gp文件, 返回list
 *
 * @param $file
 * @return array
 */
function gp_list($file)
{
    $handler = fopen($file, "rb");
    $list = [];
    while (!feof($handler)) {
        $line = trim(fgets($handler));
        list($pkg, $vcode, $vname) = explode(",", $line);
        $app = [];
        $app['pkg'] = $pkg;
        $app['vcode'] = $vcode;
        $app['vname'] = $vname;
        $list[] = $app;
    }
    return $list;
}

/**
 * 根据线上文件返回list
 *
 * @param $file
 * @return array
 */
function online_list($file)
{
    $handler = fopen($file, "rb");
    $list = [];
    while (!feof($handler)) {
        $line = trim(fgets($handler));
        list($pkg, $vcode, $sha1) = explode(" | ", $line);
        $app = [];
        $app['pkg'] = $pkg;
        $app['vcode'] = $vcode;
        $app['sha1'] = $sha1;
        $list[] = $app;
    }
    return $list;
}

/**
 * 根据apkpure的文件返回list
 *
 * @param $file
 * @return array
 */
function apkpure_list($file)
{
    $handler = fopen($file, "rb");
    $list = [];
    while (!feof($handler)) {
        $line = trim(fgets($handler));
        if (empty($line))
            continue;
        $line = json_decode($line, true);
        $app = [];
        $app['pkg'] = $line['package_name'];
        $app['vcode'] = $line['version_code'];
        $app['vname'] = $line['version_name'];
        $list[] = $app;
    }
    return $list;
}

/**
 * 比较gp与线上的不同数据
 *
 * @param $gp_file
 * @param $online_file
 * * @param $diff_file
 */
function cmp_gp_online($gp_file, $online_file, $diff_file)
{
    $gp_list = gp_list($gp_file);
    $online_list = online_list($online_file);
    $total_apk_num = 0;
    $diff_apk_num = 0;
    foreach ($gp_list as $gp) {
        $pkg = $gp['pkg'];
        $total_apk_num++;
        foreach ($online_list as $online) {
            if ($pkg == $online['pkg']) {
                if ($gp['vcode'] != $online['vcode']) {
                    $diff_apk_num++;
                    $diff = [];
                    $diff['pkg'] = $pkg;
                    $diff['gp'] = $gp;
                    $diff['online'] = $online;
                    file_put_contents($diff_file, json_encode($diff) . PHP_EOL, FILE_APPEND | LOCK_EX);
                }
                break;
            }
        }
    }
    $data = 'Total app num: ' . $total_apk_num . ', diff apk num : ' . $diff_apk_num . PHP_EOL;
    file_put_contents($diff_file, $data, FILE_APPEND | LOCK_EX);
}

/**
 * 比较gp与apkpure的不同数据
 *
 * @param $gp_file
 * @param $apkpure_file
 * * @param $diff_file
 */
function cmp_gp_apkpure($gp_file, $apkpure_file, $diff_file)
{
    $gp_list = gp_list($gp_file);
    $apkpure_list = apkpure_list($apkpure_file);
    $total_apk_num = 0;
    $diff_apk_num = 0;
    foreach ($gp_list as $gp) {
        $pkg = $gp['pkg'];
        $total_apk_num++;
        foreach ($apkpure_list as $apkpure) {
            if ($pkg == $apkpure['pkg']) {
                if ($gp['vcode'] != $apkpure['vcode']) {
                    $diff_apk_num++;
                    $diff = [];
                    $diff['pkg'] = $pkg;
                    $diff['gp'] = $gp;
                    $diff['apkpure'] = $apkpure;
                    file_put_contents($diff_file, json_encode($diff) . PHP_EOL, FILE_APPEND | LOCK_EX);
                }
                break;
            }
        }
    }
    $data = 'Total app num: ' . $total_apk_num . ', diff apk num : ' . $diff_apk_num . PHP_EOL;
    file_put_contents($diff_file, $data, FILE_APPEND | LOCK_EX);
}

$conf = $argv[1];
$base_path = dirname(__FILE__);
// 下载gp上的包的信息
//download_gp($conf, $base_path . '/pname.log', $base_path . '/gp.log');
// 比较gp与线上的数据
cmp_gp_online($base_path . '/gp.log', $base_path . '/pname.log', $base_path . '/diff.gp.online.log');
// 比较gp与apkpure的数据
cmp_gp_apkpure($base_path . '/gp.log', $base_path . '/pname.diff.log.request', $base_path . '/diff.gp.apkpure.log');