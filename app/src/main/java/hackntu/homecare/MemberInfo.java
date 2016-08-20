package hackntu.homecare;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by kai-chuan on 8/20/16.
 */
public class MemberInfo {
    private static final String TAG = "MemberInfo";

    private Long startTime;
    private String memberName;
    private String memberStatus;

    public String getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    @Override
    public String toString() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");

        // Create a calendar object that will convert the date and time value in milliseconds to date.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startTime);
        String time = formatter.format(calendar.getTime());
        return  "StartTime: " + time + "\n" +
                "Name: " + memberName + "\n" +
                "Status: " + memberStatus;
    }
}
