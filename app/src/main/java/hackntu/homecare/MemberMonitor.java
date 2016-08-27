package hackntu.homecare;

import android.os.SystemClock;
import android.util.Log;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import java.util.Calendar;

import onepv1.ClientOnep;
import onepv1.OneException;
import onepv1.Result;

/**
 * Created by kai-chuan on 8/25/16.
 */
public class MemberMonitor {
    private static final String TAG = "MemberMonitor";
    private MemberInfo mMemberInfo;
    private InfoManager mInfoManager;
    private boolean threadStrated = false;

    MemberMonitor (MemberInfo memberInfo, InfoManager infoManager) {
        mMemberInfo = memberInfo;
        mInfoManager = infoManager;
    }

    public void startMonitoring() {
        Thread thread = new Thread(monitorThread);
        thread.start();
        threadStrated = true;
    }

    public void stopMonitoring() {
        threadStrated = false;
    }

    private Runnable monitorThread = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Thread start");
            String cik = "903261ecc4869104a8de3f8750ff96ef537f7c38";
            ClientOnep conn = new ClientOnep("http://m2.exosite.com/api:v1/rpc/process", 3, cik);

            try {
                while (threadStrated) {
                    Result res = conn.read("Humidity_SHT30");
                    if (res.getStatus() == Result.OK){
                        String read = res.getMessage();
                        JSONArray dataarr = (JSONArray) JSONValue.parse(read);
                        JSONArray data1 = (JSONArray)dataarr.get(0);
                        Log.d(TAG, "Humidity SHT30= " + data1.get(1) + " is read.");
                        mMemberInfo.setMemberHumidity(data1.get(1).toString());
                    }

                    res = conn.read("Temperature_MCP9800");
                    if (res.getStatus() == Result.OK){
                        String read = res.getMessage();
                        JSONArray dataarr = (JSONArray) JSONValue.parse(read);
                        JSONArray data1 = (JSONArray)dataarr.get(0);
                        Log.d(TAG, "Temperature MCP9800= " + data1.get(1) + " is read.");
                        mMemberInfo.setMemberTemperature(data1.get(1).toString());
                    }
                    Log.d(TAG, "Sleep 10 seconds");
                    SystemClock.sleep(10000);

                    Calendar current = Calendar.getInstance();
                    if (current.getTimeInMillis()-mMemberInfo.getStartTime()>1800000) {
                        Log.d(TAG, "Diff time over 30 min");
                        if (Float.parseFloat(mMemberInfo.getMemberHumidity())>60.0 &&
                                Float.parseFloat(mMemberInfo.getMemberTemperature())>45.0) {
                            Log.d(TAG, "Humidity is over 60, is danger");
                            mMemberInfo.setMemberStatus("Danger");
                        } else {
                            Log.d(TAG, "Humidity is lower than 60 and Temperature is lower than 45, remove member");
                            mInfoManager.removeMemberInfo(mMemberInfo);
                            threadStrated = false;
                            break;
                        }
                    }
                }
            } catch (OneException e) {
                e.printStackTrace();
            }
        }
    };
}
