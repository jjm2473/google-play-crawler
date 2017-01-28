package com.akdeniz.googleplaycrawler;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.message.BasicNameValuePair;

import com.akdeniz.googleplaycrawler.GooglePlay.AndroidAppDeliveryData;
import com.akdeniz.googleplaycrawler.GooglePlay.AndroidCheckinRequest;
import com.akdeniz.googleplaycrawler.GooglePlay.AndroidCheckinResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.BrowseResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsRequest;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsRequest.Builder;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.BuyResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DeviceConfigurationProto;
import com.akdeniz.googleplaycrawler.GooglePlay.HttpCookie;
import com.akdeniz.googleplaycrawler.GooglePlay.ListResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.ResponseWrapper;
import com.akdeniz.googleplaycrawler.GooglePlay.ReviewResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.SearchResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.UploadDeviceConfigRequest;
import com.akdeniz.googleplaycrawler.GooglePlay.UploadDeviceConfigResponse;

/**
 * This class provides
 * <code>checkin, search, details, bulkDetails, browse, list and download</code>
 * capabilities. It uses <code>Apache Commons HttpClient</code> for POST and GET
 * requests.
 * <p>
 * <p>
 * <b>XXX : DO NOT call checkin, login and download consecutively. To allow
 * server to catch up, sleep for a while before download! (5 sec will do!) Also
 * it is recommended to call checkin once and use generated android-id for
 * further operations.</b>
 * </p>
 *
 * @author akdeniz
 */
public class GooglePlayAPI {

    private static final String CHECKIN_URL = "https://android.clients.google.com/checkin";
    private static final String URL_LOGIN = "https://android.clients.google.com/auth";
    private static final String C2DM_REGISTER_URL = "https://android.clients.google.com/c2dm/register2";
    private static final String FDFE_URL = "https://android.clients.google.com/fdfe/";
    private static final String LIST_URL = FDFE_URL + "list";
    private static final String BROWSE_URL = FDFE_URL + "browse";
    private static final String DETAILS_URL = FDFE_URL + "details";
    private static final String SEARCH_URL = FDFE_URL + "search";
    private static final String BULKDETAILS_URL = FDFE_URL + "bulkDetails";
    private static final String PURCHASE_URL = FDFE_URL + "purchase";
    private static final String REVIEWS_URL = FDFE_URL + "rev";
    private static final String ADDREVIEW_URL = FDFE_URL + "addReview";
    private static final String UPLOADDEVICECONFIG_URL = FDFE_URL + "uploadDeviceConfig";
    private static final String RECOMMENDATIONS_URL = FDFE_URL + "rec";

    private static final String ACCOUNT_TYPE_HOSTED_OR_GOOGLE = "HOSTED_OR_GOOGLE";

    public static enum REVIEW_SORT {
        NEWEST(0), HIGHRATING(1), HELPFUL(2);

        public int value;

        private REVIEW_SORT(int value) {
            this.value = value;
        }
    }

    public static enum RECOMMENDATION_TYPE {
        ALSO_VIEWED(1), ALSO_INSTALLED(2);

        public int value;

        private RECOMMENDATION_TYPE(int value) {
            this.value = value;
        }
    }

    private String token;
    private String androidID;
    private String email;
    private String password;
    private HttpClient client;
    private String securityToken;
    private String localization;

    /**
     * Default constructor. ANDROID ID and Authentication token must be supplied
     * before any other operation.
     */
    public GooglePlayAPI() {
    }

    /**
     * Constructs a ready to login {@link GooglePlayAPI}.
     */
    public GooglePlayAPI(String email, String password, String androidID) {
        this(email, password);
        this.setAndroidID(androidID);
    }

    /**
     * If this constructor is used, Android ID must be generated by calling
     * <code>checkin()</code> or set by using <code>setAndroidID</code> before
     * using other abilities.
     */
    public GooglePlayAPI(String email, String password) {
        this.setEmail(email);
        this.password = password;
        setClient(new DefaultHttpClient(getConnectionManager()));
    }

    /**
     * Connection manager to allow concurrent connections.
     *
     * @return {@link ClientConnectionManager} instance
     */
    public static ClientConnectionManager getConnectionManager() {
        PoolingClientConnectionManager connManager =
                new PoolingClientConnectionManager(SchemeRegistryFactory.createDefault());
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(30);
        return connManager;
    }

    /**
     * Performs authentication on "ac2dm" service and match up android id,
     * security token and email by checking them in on this server.
     * <p>
     * This function sets check-inded android ID and that can be taken either by
     * using <code>getToken()</code> or from returned
     * {@link AndroidCheckinResponse} instance.
     */
    public AndroidCheckinResponse checkin(Properties device) throws Exception {

        // this first checkin is for generating android-id
        AndroidCheckinResponse checkinResponse = postCheckin(Utils.generateDefaultAndroidCheckinRequest().toByteArray());
        this.setAndroidID(BigInteger.valueOf(checkinResponse.getAndroidId()).toString(16));
        setSecurityToken((BigInteger.valueOf(checkinResponse.getSecurityToken()).toString(16)));

        String c2dmAuth = loginAC2DM();

        AndroidCheckinRequest.Builder checkInbuilder;
        String defFlag = device.getProperty("default");
        if ((defFlag != null) && (defFlag.equals("true"))) {
            checkInbuilder = AndroidCheckinRequest.newBuilder(Utils.generateDefaultAndroidCheckinRequest());
        } else {
            checkInbuilder = AndroidCheckinRequest.newBuilder(Utils.generateAndroidCheckinRequest(device));
        }

        AndroidCheckinRequest build = checkInbuilder.setId(new BigInteger(this.getAndroidID(), 16).longValue())
                .setSecurityToken(new BigInteger(getSecurityToken(), 16).longValue()).addAccountCookie("[" + getEmail() + "]")
                .addAccountCookie(c2dmAuth).build();
        // this is the second checkin to match credentials with android-id
        return postCheckin(build.toByteArray());
    }

    /**
     * Logins AC2DM server and returns authentication string.
     * <p>
     * <p>
     * client_sig is SHA1 digest of encoded certificate on
     * <i>GoogleLoginService(package name : com.google.android.gsf)</i> system APK.
     * But google doesn't seem to care of value of this parameter.
     */
    public String loginAC2DM() throws IOException {
        HttpEntity c2dmResponseEntity = executePost(URL_LOGIN, new String[][]{{"Email", this.getEmail()},
                {"Passwd", this.password}, {"service", "ac2dm"}, {"accountType", ACCOUNT_TYPE_HOSTED_OR_GOOGLE},
                {"has_permission", "1"}, {"source", "android"}, {"app", "com.google.android.gsf"},
                {"device_country", "us"}, {"device_country", "us"}, {"lang", "en"}, {"sdk_version", "16"}, {"client_sig", "38918a453d07199354f8b19af05ec6562ced5788"},}, null);

        Map<String, String> c2dmAuth = Utils.parseResponse(new String(Utils.readAll(c2dmResponseEntity.getContent())));
        return c2dmAuth.get("Auth");

    }

    public Map<String, String> c2dmRegister(String application, String sender) throws IOException {

        String c2dmAuth = loginAC2DM();
        String[][] data = new String[][]{{"app", application}, {"sender", sender}, {"device", new BigInteger(this.getAndroidID(), 16).toString()}};
        HttpEntity responseEntity = executePost(C2DM_REGISTER_URL, data, getHeaderParameters(c2dmAuth, null, "21"));
        return Utils.parseResponse(new String(Utils.readAll(responseEntity.getContent())));
    }

    /**
     * Equivalent of <code>setToken</code>. This function does not performs
     * authentication, it simply sets authentication token.
     */
    public void login(String token) throws Exception {
        setToken(token);
    }

    /**
     * Authenticates on server with given email and password and sets
     * authentication token. This token can be used to login instead of using
     * email and password every time.
     */
    public void login() throws Exception {

        HttpEntity responseEntity = executePost(URL_LOGIN, new String[][]{
                {"Email", this.getEmail()}, {"Passwd", this.password},
                {"service", "androidmarket"}, {"accountType", ACCOUNT_TYPE_HOSTED_OR_GOOGLE},
                {"has_permission", "1"}, {"source", "android"},
                {"androidId", this.getAndroidID()}, {"app", "com.android.vending"},
                {"device_country", "en"}, {"lang", "en"}, {"sdk_version", "22"},
                {"client_sig", "38918a453d07199354f8b19af05ec6562ced5788"},}, null);

        Map<String, String> response = Utils.parseResponse(new String(Utils.readAll(responseEntity.getContent())));
        if (response.containsKey("Auth")) {
            setToken(response.get("Auth"));
        } else {
            throw new GooglePlayException("Authentication failed!");
        }
    }

    /**
     * Equivalent of <code>search(query, null, null)</code>
     */
    public SearchResponse search(String query) throws IOException {
        return search(query, null, null);
    }

    /**
     * Fetches a search results for given query. Offset and numberOfResult
     * parameters are optional and <code>null</code> can be passed!
     */
    public SearchResponse search(String query, Integer offset, Integer numberOfResult) throws IOException {

        ResponseWrapper responseWrapper = executeGETRequest(SEARCH_URL,
                new String[][]{{"c", "3"}, {"q", query}, {"o", (offset == null) ? null : String.valueOf(offset)},
                        {"n", (numberOfResult == null) ? null : String.valueOf(numberOfResult)},});

        return responseWrapper.getPayload().getSearchResponse();
    }

    /**
     * Fetches detailed information about passed package name. If it is needed
     * to fetch information about more than one application, consider to use
     * <code>bulkDetails</code>.
     */
    public DetailsResponse details(String packageName) throws IOException {
        ResponseWrapper responseWrapper = executeGETRequest(DETAILS_URL, new String[][]{{"doc", packageName},});

        return responseWrapper.getPayload().getDetailsResponse();
    }

    /**
     * Equivalent of details but bulky one!
     */
    public BulkDetailsResponse bulkDetails(List<String> packageNames) throws IOException {

        Builder bulkDetailsRequestBuilder = BulkDetailsRequest.newBuilder();
        bulkDetailsRequestBuilder.addAllDocid(packageNames);

        ResponseWrapper responseWrapper = executePOSTRequest(BULKDETAILS_URL, bulkDetailsRequestBuilder.build().toByteArray(),
                "application/x-protobuf");

        return responseWrapper.getPayload().getBulkDetailsResponse();
    }

    /**
     * Fetches available categories
     */
    public BrowseResponse browse() throws IOException {

        return browse(null, null);
    }

    public BrowseResponse browse(String categoryId, String subCategoryId) throws IOException {

        ResponseWrapper responseWrapper = executeGETRequest(BROWSE_URL, new String[][]{{"c", "3"}, {"cat", categoryId},
                {"ctr", subCategoryId}});

        return responseWrapper.getPayload().getBrowseResponse();
    }

    /**
     * Equivalent of <code>list(categoryId, null, null, null)</code>. It fetches
     * sub-categories of given category!
     */
    public ListResponse list(String categoryId) throws IOException {
        return list(categoryId, null, null, null);
    }

    /**
     * Fetches applications within supplied category and sub-category. If
     * <code>null</code> is given for sub-category, it fetches sub-categories of
     * passed category.
     * <p>
     * Default values for offset and numberOfResult are "0" and "20"
     * respectively. These values are determined by Google Play Store.
     */
    public ListResponse list(String categoryId, String subCategoryId, Integer offset, Integer numberOfResult) throws IOException {
        ResponseWrapper responseWrapper = executeGETRequest(LIST_URL, new String[][]{{"c", "3"}, {"cat", categoryId},
                {"ctr", subCategoryId}, {"o", (offset == null) ? null : String.valueOf(offset)},
                {"n", (numberOfResult == null) ? null : String.valueOf(numberOfResult)},});

        return responseWrapper.getPayload().getListResponse();
    }

    /**
     * Downloads given application package name, version and offer type. Version
     * code and offer type can be fetch by <code>details</code> interface.
     **/
    public InputStream download(String packageName, int versionCode, int offerType, String sdk) throws IOException {

        BuyResponse buyResponse = purchase(packageName, versionCode, offerType, sdk);

        AndroidAppDeliveryData appDeliveryData = buyResponse.getPurchaseStatusResponse().getAppDeliveryData();

        String downloadUrl = appDeliveryData.getDownloadUrl();
        HttpCookie downloadAuthCookie = appDeliveryData.getDownloadAuthCookie(0);

        return executeDownload(downloadUrl, downloadAuthCookie.getName() + "=" + downloadAuthCookie.getValue());

    }

    /**
     * Posts given check-in request content and returns
     * {@link AndroidCheckinResponse}.
     */
    private AndroidCheckinResponse postCheckin(byte[] request) throws IOException {

        HttpEntity httpEntity = executePost(CHECKIN_URL, new ByteArrayEntity(request), new String[][]{
                {"User-Agent", "Android-Checkin/2.0 (generic JRO03E); gzip"}, {"Host", "android.clients.google.com"},
                {"Content-Type", "application/x-protobuffer"}});
        return AndroidCheckinResponse.parseFrom(httpEntity.getContent());
    }

    /**
     * This function is used for fetching download url and donwload cookie,
     * rather than actual purchasing.
     */
    private BuyResponse purchase(String packageName, int versionCode, int offerType, String sdk) throws IOException {

        ResponseWrapper responseWrapper = executePOSTRequest(PURCHASE_URL, new String[][]{{"ot", String.valueOf(offerType)},
                {"doc", packageName}, {"vc", String.valueOf(versionCode)},}, sdk);

        return responseWrapper.getPayload().getBuyResponse();
    }

    /**
     * Fetches url content by executing GET request with provided cookie string.
     */
    public InputStream executeDownload(String url, String cookie) throws IOException {

        String[][] headerParams = new String[][]{{"Cookie", cookie},
                {"User-Agent", "AndroidDownloadManager/5.1.1 (Linux; U; Android 5.1.1; SAMSUNG-SM-G530AZ Build/LMY48B)"},};
        String ua = "User-Agent: AndroidDownloadManager/5.1.1 (Linux; U; Android 5.1.1; SAMSUNG-SM-G530AZ Build/LMY48B)";
        System.out.println("curl -IL \"" + url + "\" -H \"" + ua + "\"" + " -H \"Cookie: " + cookie + "\"");

        HttpEntity httpEntity = executeGet(url, null, headerParams);
        return httpEntity.getContent();
    }

    /**
     * Fetches the reviews of given package name by sorting passed choice.
     * <p>
     * Default values for offset and numberOfResult are "0" and "20"
     * respectively. These values are determined by Google Play Store.
     */
    public ReviewResponse reviews(String packageName, REVIEW_SORT sort, Integer offset, Integer numberOfResult)
            throws IOException {
        ResponseWrapper responseWrapper = executeGETRequest(REVIEWS_URL,
                new String[][]{{"doc", packageName}, {"sort", (sort == null) ? null : String.valueOf(sort.value)},
                        {"o", (offset == null) ? null : String.valueOf(offset)},
                        {"n", (numberOfResult == null) ? null : String.valueOf(numberOfResult)}});

        return responseWrapper.getPayload().getReviewResponse();
    }

    /**
     * Uploads device configuration to google server so that can be seen from
     * web as a registered device!!
     *
     * @see https://play.google.com/store/account
     */
    public UploadDeviceConfigResponse uploadDeviceConfig(Properties device) throws Exception {

        UploadDeviceConfigRequest request;
        String defFlag = device.getProperty("default");
        if ((defFlag != null) && (defFlag.equals("true"))) {
            request = UploadDeviceConfigRequest.newBuilder()
                    .setDeviceConfiguration(Utils.getDefaultDeviceConfigurationProto()).build();
        } else {
            request = UploadDeviceConfigRequest.newBuilder()
                    .setDeviceConfiguration(Utils.getDeviceConfigurationProto(device)).build();
        }
        ResponseWrapper responseWrapper = executePOSTRequest(UPLOADDEVICECONFIG_URL, request.toByteArray(),
                "application/x-protobuf");
        return responseWrapper.getPayload().getUploadDeviceConfigResponse();
    }

    /**
     * Fetches the recommendations of given package name.
     * <p>
     * Default values for offset and numberOfResult are "0" and "20"
     * respectively. These values are determined by Google Play Store.
     */
    public ListResponse recommendations(String packageName, RECOMMENDATION_TYPE type, Integer offset, Integer numberOfResult)
            throws IOException {
        ResponseWrapper responseWrapper = executeGETRequest(RECOMMENDATIONS_URL,
                new String[][]{{"c", "3"}, {"doc", packageName}, {"rt", (type == null) ? null : String.valueOf(type.value)},
                        {"o", (offset == null) ? null : String.valueOf(offset)},
                        {"n", (numberOfResult == null) ? null : String.valueOf(numberOfResult)}});

        return responseWrapper.getPayload().getListResponse();
    }

    /* =======================Helper Functions====================== */

    /**
     * Executes GET request and returns result as {@link ResponseWrapper}.
     * Standard header parameters will be used for request.
     *
     * @see getHeaderParameters
     */
    private ResponseWrapper executeGETRequest(String path, String[][] datapost) throws IOException {

        HttpEntity httpEntity = executeGet(path, datapost, getHeaderParameters(this.getToken(), null, "21"));
        return GooglePlay.ResponseWrapper.parseFrom(httpEntity.getContent());

    }

    /**
     * Executes POST request and returns result as {@link ResponseWrapper}.
     * Standard header parameters will be used for request.
     *
     * @see getHeaderParameters
     */
    private ResponseWrapper executePOSTRequest(String path, String[][] datapost, String sdk) throws IOException {

        HttpEntity httpEntity = executePost(path, datapost, getHeaderParameters(this.getToken(), null, sdk));
        return GooglePlay.ResponseWrapper.parseFrom(httpEntity.getContent());

    }

    /**
     * Executes POST request and returns result as {@link ResponseWrapper}.
     * Content type can be specified for given byte array.
     */
    private ResponseWrapper executePOSTRequest(String url, byte[] datapost, String contentType) throws IOException {

        HttpEntity httpEntity = executePost(url, new ByteArrayEntity(datapost), getHeaderParameters(this.getToken(), contentType, "21"));
        return GooglePlay.ResponseWrapper.parseFrom(httpEntity.getContent());

    }

    /**
     * Executes POST request on given URL with POST parameters and header
     * parameters.
     */
    private HttpEntity executePost(String url, String[][] postParams, String[][] headerParams) throws IOException {

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();

        for (String[] param : postParams) {
            if (param[0] != null && param[1] != null) {
                formparams.add(new BasicNameValuePair(param[0], param[1]));
            }
        }

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");

        return executePost(url, entity, headerParams);
    }

    /**
     * Executes POST request on given URL with {@link HttpEntity} typed POST
     * parameters and header parameters.
     */
    private HttpEntity executePost(String url, HttpEntity postData, String[][] headerParams) throws IOException {
        HttpPost httppost = new HttpPost(url);

        if (headerParams != null) {
            for (String[] param : headerParams) {
                if (param[0] != null && param[1] != null) {
                    httppost.setHeader(param[0], param[1]);
                }
            }
        }

        httppost.setEntity(postData);

        return executeHttpRequest(httppost);
    }

    /**
     * Executes GET request on given URL with GET parameters and header
     * parameters.
     */
    private HttpEntity executeGet(String url, String[][] getParams, String[][] headerParams) throws IOException {

        if (getParams != null) {
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();

            for (String[] param : getParams) {
                if (param[0] != null && param[1] != null) {
                    formparams.add(new BasicNameValuePair(param[0], param[1]));
                }
            }

            url = url + "?" + URLEncodedUtils.format(formparams, "UTF-8");
        }

        HttpGet httpget = new HttpGet(url);

        if (headerParams != null) {
            for (String[] param : headerParams) {
                if (param[0] != null && param[1] != null) {
                    httpget.setHeader(param[0], param[1]);
                }
            }
        }

        return executeHttpRequest(httpget);
    }

    /**
     * Executes given GET/POST request
     */
    private HttpEntity executeHttpRequest(HttpUriRequest request) throws ClientProtocolException, IOException {
        HttpResponse response = getClient().execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new GooglePlayException(new String(Utils.readAll(response.getEntity().getContent())));
        }

        return response.getEntity();
    }

    /**
     * Gets header parameters for GET/POST requests. If no content type is
     * given, default one is used!
     */
    private String[][] getHeaderParameters(String token, String contentType, String sdk) {

        Map<String, String> sdkMap = new HashMap<>();
        sdkMap.put("10", "2.3.3 Gingerbread");
        sdkMap.put("11", "3.0 Honeycomb");
        sdkMap.put("12", "3.1 Honeycomb");
        sdkMap.put("13", "3.2 Honeycomb");
        sdkMap.put("14", "4.0 Ice Cream Sandwich");
        sdkMap.put("15", "4.0.3 Ice Cream Sandwich");
        sdkMap.put("16", "4.1 Jelly Bean");
        sdkMap.put("17", "4.2 Jelly Bean");
        sdkMap.put("18", "4.3 Jelly Bean");
        sdkMap.put("19", "4.4 KitKat");
        sdkMap.put("20", "4.4W");
        sdkMap.put("21", "5.1.11");
        sdkMap.put("22", "5.1 Lollipop");
        sdkMap.put("23", "6.0 Marshmallow");
        sdkMap.put("24", "7.0 Nougat");

        Map<String, String> vcodeMap = new HashMap<>();
        vcodeMap.put("21", "80310011");
        vcodeMap.put("23", "80682400");
        // 该信息为我的调试机器实际数据
        String ua = "Android-Finsky/" + sdkMap.get(sdk) + " (versionCode=" + vcodeMap.get(sdk) + ",sdk=" + sdk + ",device=AQ5001,hardware=mt6582,product=AQ5001,build=LRX21M:user)";
        // String ua = "Android-Finsky/" + sdkMap.get(sdk) + " (api=" + sdkMap.get(sdk) + ",versionCode=8016014,sdk=" + sdk + ",device=GT-I9300,hardware=aries,product=GT-I9300)"},
        //System.out.printf(ua, sdkMap.get(sdk), vcodeMap.get(sdk), sdk, "AQ5001", "mt6582", "AQ5001", "LRX21M", "user");
        return new String[][]{
                {"Accept-Language", getLocalization() != null ? getLocalization() : "en-EN"},
                {"Authorization", "GoogleLogin auth=" + token},
                {"X-DFE-Enabled-Experiments", "cl:billing.select_add_instrument_by_default"},
                {"X-DFE-Unsupported-Experiments", "nocache:billing.use_charging_poller,market_emails,buyer_currency,prod_baseline,checkin.set_asset_paid_app_field,shekel_test,content_ratings,buyer_currency_in_app,nocache:encrypted_apk,recent_changes"},
                {"X-DFE-Device-Id", this.getAndroidID()},
                {"X-DFE-Client-Id", "am-android-google"},
                {"User-Agent", ua},
                {"X-DFE-SmallestScreenWidthDp", "320"},
                {"X-DFE-Filter-Level", "3"},
                {"Host", "android.clients.google.com"},
                {"Content-Type", (contentType != null) ? contentType : "application/x-www-form-urlencoded; charset=UTF-8"}};
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAndroidID() {
        return androidID;
    }

    public void setAndroidID(String androidID) {
        this.androidID = androidID;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }

    public HttpClient getClient() {
        return client;
    }

    /**
     * Sets {@link HttpClient} instance for internal usage of GooglePlayAPI.
     * It is important to note that this instance should allow concurrent connections.
     *
     * @param client
     * @see getConnectionManager
     */
    public void setClient(HttpClient client) {
        this.client = client;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLocalization() {
        return localization;
    }

    /**
     * Localization string that will be used in each request to server. Using this option
     * you can fetch localized informations such as reviews and descriptions.
     * <p>
     * Note that changing this value has no affect on localized application list that
     * server provides. It depends on only your IP location.
     * <p>
     *
     * @param localization can be <b>en-EN, en-US, tr-TR, fr-FR ... (default : en-EN)</b>
     */
    public void setLocalization(String localization) {
        this.localization = localization;
    }

}
