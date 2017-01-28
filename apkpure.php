<?php

// 根据报名获取apkpure上面对应的应用版本信息
if ($argc != 3 || empty(trim($argv[1])) || empty(trim($argv[2]))) {
    echo 'Usage: ' . __FILE__ . ' package_name save_dir' . PHP_EOL;
    echo 'Eg: php apkpure.php com.facebook.katana /tmp/ ' . PHP_EOL;
    return;
}

define('API_URL', 'https://api.pureapk.com');
$package = trim($argv[1]);
$save_dir = trim($argv[2]);
$save_name = $save_dir . '/' . $package . '.apk';

/**
 * 根据包名搜索apk, 并且返回详情
 *
 * @param string $package 包名
 * @return array apk数据
 */
function search($package = '')
{
    if (empty($package)) {
        echo 'package can not be empty' . PHP_EOL;
        return false;
    }
    echo 'searching ' . $package . PHP_EOL;
    // 1.搜索对应的包名
    $cmd = 'curl -H "X-Auth-Key: qNKrYmW8SSUqJ73k3P2yfMxRTo3sJTR" ' . API_URL . '/m/v1/search/query\?hl\=zh-CN\&aid\=com.apkpure.aegon\&flavor\=officialMobvista\&cv\=501\&sv\=21\&key\=' . $package . '\&limit\=20\&start\=0';
    $data = `$cmd`;
    $data = json_decode($data, true);
    $data = $data['result'];

    $result = [];
    foreach ($data as $app) {
        $pkg = $app['package_name'];
        if ($pkg == $package) {
            $result['package_name'] = $pkg;
            $result['version_name'] = $app['version_name'];
            $result['version_code'] = $app['version_code'];
            $result['source_id'] = $app['source_id'];
            $result['sign'] = $app['sign'][0];
            $result['source_name'] = $app['source_name'];
            $result['asset'] = $app['asset'];
            break;
        }
    }

    print_r($result);
    // 2.检查是不是来自gp
    if ($result['source_id'] != 1) {
        echo 'This app is not from google play...' . PHP_EOL;
    } else {
        return $result;
    }
}

/**
 * 下载apk
 *
 * @param string $package 包名
 * @param $save_name 保存文件
 */
function download($package = '', $save_name)
{
    $apk = search($package);
    $download_url = $apk['asset']['url'];
    `curl "$download_url" > $save_name`;
    echo 'download finished.' . PHP_EOL;
}

/**
 * 根据包名返回该包的所有版本数据
 *
 * @param string $package
 */
function version($package = '')
{
    $apk = search($package);
    $cmd = 'curl -H "X-Auth-Key: qNKrYmW8SSUqJ73k3P2yfMxRTo3sJTR" -H "Content-Type: application/json; charset=utf-8" -X POST --data \'{"application_id":"com.apkpure.aegon","argument":{"package_name":"' . $apk['package_name'] . '","signatures":["' . $apk['sign'] . '"],"version_code":' . $apk['version_code'] . '},"flavor":"officialMobvista","client_version":501,"sdk_version":21}\' https://api.pureapk.com/m/v1/app/version?hl=zh-CN&aid=com.apkpure.aegon&flavor=officialMobvista&cv=501&sv=21';
    $data = `$cmd`;
    $data = json_decode($data, true);
    return $data['result'];
}

/**
 * 比较线上的数据和apkpure的数据
 * @param $source 源文件
 * @param $destination 目标文件
 */
function compare($source, $destination)
{
    $handler = fopen($source, "rb");
    $total_apk_num = 0;
    $correct_apk_num = 0;
    while (!feof($handler)) {
        $line = trim(fgets($handler));
        if (empty($line)) {
            continue;
        }
        list($pkg, $vcode, $sha1) = explode(" | ", $line);
        $total_apk_num++;
        // 开始比较
        $info = search($pkg);
        // 保存请求结果
        file_put_contents($destination . '.request', json_encode($info) . PHP_EOL, FILE_APPEND | LOCK_EX);
        if ($info['version_code'] == $vcode && $info['asset']['sha1'] == $sha1) {
            $correct_apk_num++;
        } else {
            file_put_contents($destination, $line . PHP_EOL, FILE_APPEND | LOCK_EX);
        }
    }
    fclose($handler);
    echo 'Total apk num : ' . $total_apk_num . PHP_EOL;
    echo 'Correct apk num : ' . $correct_apk_num . PHP_EOL;
}

/**
 * 查询不同的包是否出现在历史列表里面
 *
 * @param $source
 * @param $destination
 */
function diff($source, $destination)
{
    $handler = fopen($source, "rb");
    $total_apk_num = 0;
    $correct_apk_num = 0;
    while (!feof($handler)) {
        $line = trim(fgets($handler));
        if (empty($line)) {
            continue;
        }
        list($pkg, $vcode, $sha1) = explode(" | ", $line);
        $total_apk_num++;
        $list = version($pkg);
        // 判断是否在历史版本中找到了该包
        $flag = false;
        foreach ($list as $app) {
            // 在历史版本中找到了该包
            if ($app['version_code'] == $vcode && $app['asset']['sha1'] == $sha1) {
                $correct_apk_num++;
                $flag = true;
                break;
            }
        }
        if ($flag == false) {
            file_put_contents($destination, $line . PHP_EOL, FILE_APPEND | LOCK_EX);
        }
    }
    fclose($handler);
    echo 'Total apk num : ' . $total_apk_num . PHP_EOL;
    echo 'Correct apk num : ' . $correct_apk_num . PHP_EOL;
}

// download($package, $save_name);
$base_path = dirname(__FILE__);
// compare($base_path . '/pname.log', $base_path . '/pname.diff.log');
diff($base_path . '/pname.diff.log', $base_path . '/pname.diff.version.log');