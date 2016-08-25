package hackntu.homecare;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

/**
 * Created by kai-chuan on 8/20/16.
 */
public class InfoManager {
    private static final String TAG = "InfoManager";
    private final String MEMBER_DATA_XML = "memberdata.xml";
    private final int INPUT_BUFFER_SIZE = 2048;
    private ArrayList<MemberInfo> memberInfos;
    private Context mContext;

    public InfoManager(Context context) {
        mContext = context;
        memberInfos = new ArrayList<MemberInfo>();
        syncMemberInfos();
    }

    private void syncMemberInfos() {
        String xmldata = getXMLData();
        MemberInfo memberInfo = null;
        boolean inTask = false;
        String textValue = null;
        if (xmldata!=null) {
            try {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser xpp = factory.newPullParser();
                xpp.setInput(new StringReader(xmldata));
                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tagName = xpp.getName();
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            //Log.d(TAG, "starting tag for " + tagName);
                            if (tagName.equalsIgnoreCase("member")) {
                                memberInfo = new MemberInfo();
                                inTask = true;
                            }
                            break;
                        case XmlPullParser.TEXT:
                            textValue = xpp.getText();
                            break;
                        case XmlPullParser.END_TAG:
                            if (inTask) {
                                //Log.d(TAG, "ending tag for " + tagName);
                                if (tagName.equalsIgnoreCase("name")) {
                                    memberInfo.setMemberName(textValue);
                                }
                                if (tagName.equalsIgnoreCase("time")) {
                                    long time = Long.parseLong(textValue);
                                    memberInfo.setStartTime(time);
                                }
                                if (tagName.equalsIgnoreCase("status")) {
                                    memberInfo.setMemberStatus(textValue);
                                }
                            }
                            break;
                        default:
                            // Do nothing
                    }
                    eventType = xpp.next();
                }
            }catch(XmlPullParserException e){
                    e.printStackTrace();
            }catch(IOException e){
                    e.printStackTrace();
            }
        }
    }

    private String getXMLData() {
        String xmldata = null;
        if (isExternalStorageReadable()) {
            File file = new File(mContext.getExternalFilesDir(null), MEMBER_DATA_XML);
            try {
                FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis);
                // READ STRING OF UNKNOWN LENGTH
                StringBuilder sb = new StringBuilder();
                char[] inputBuffer = new char[INPUT_BUFFER_SIZE];
                int byteRead = -1;
                // FILL BUFFER WITH DATA
                while ((byteRead = isr.read(inputBuffer)) != -1) {
                    sb.append(inputBuffer, 0, byteRead);
                }
                // CONVERT BYTES TO STRING
                xmldata = sb.toString();
                fis.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "external storage is not readable");
        }
        return xmldata;
    }

    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public MemberInfo getMemberInfo(String name, Long time, String status) {
        MemberInfo memberInfo = new MemberInfo();
        memberInfo.setMemberName(name);
        memberInfo.setStartTime(time);
        memberInfo.setMemberStatus(status);
        memberInfo.setMemberHumidity("0");
        memberInfo.setMemberTemperature("0");
        MemberMonitor memberMonitor = new MemberMonitor(memberInfo, this);
        memberInfo.setMemberMonitor(memberMonitor);
        return memberInfo;
    }

    public ArrayList<MemberInfo> getMemberInfos() {
        Log.d(TAG, "getMemberInfos In");
        saveMemberInfos();
        return memberInfos;
    }

    public void addMemberInfo(MemberInfo memberInfo) {
        Log.d(TAG, "addMemberInfo In");
        memberInfos.add(memberInfo);
    }

    public void removeMemberInfo(MemberInfo memberInfo) {
        Log.d(TAG, "removeMemberInfo In");
        MemberMonitor memberMonitor = memberInfo.getMemberMonitor();
        memberMonitor.stopMonitoring();
        memberInfos.remove(memberInfo);
    }

    public void saveMemberInfos() {
        if (isExternalStorageWritable()) {
            File file = new File(mContext.getExternalFilesDir(null), MEMBER_DATA_XML);
            try {
                FileOutputStream fos = new FileOutputStream(file);
                XmlSerializer xmlSerializer = Xml.newSerializer();
                StringWriter writer = new StringWriter();
                xmlSerializer.setOutput(writer);
                xmlSerializer.startDocument("UTF-8", true);
                for (MemberInfo memberInfo : memberInfos) {
                    xmlSerializer.startTag("", "member");
                    xmlSerializer.startTag("", "name");
                    xmlSerializer.text(memberInfo.getMemberName());
                    xmlSerializer.endTag("", "name");
                    xmlSerializer.startTag("", "time");
                    String time =  String.valueOf(memberInfo.getStartTime());
                    xmlSerializer.text(time);
                    xmlSerializer.endTag("", "time");
                    xmlSerializer.startTag("", "status");
                    xmlSerializer.text(memberInfo.getMemberStatus());
                    xmlSerializer.endTag("", "status");
                    xmlSerializer.endTag("", "member");
                }
                xmlSerializer.endDocument();
                xmlSerializer.flush();
                String dataWrite = writer.toString();
                fos.write(dataWrite.getBytes());
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "external storage is not writable");
        }
    }

    public Long getMaxRecvTime() {
        Long max = 0L;
        for (MemberInfo info:memberInfos) {
            if (info.getStartTime()>max) max = info.getStartTime();
        }
        return max;
    }
}
