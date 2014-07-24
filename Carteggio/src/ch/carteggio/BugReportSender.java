package ch.carteggio;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.acra.ACRA;
import org.acra.ACRAConstants;
import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.util.Log;

public class BugReportSender implements ReportSender {

	    private static final String LOG_TAG = "ReportSender";
		private final Context mContext;

		private File mReportFile;
		private File mReportDirectory;
		
		
	    public BugReportSender(Context ctx) {
	        mContext = ctx;
	        
	        mReportDirectory = new File(mContext.getFilesDir(), "bugreports");
	        
	        mReportDirectory.mkdir();
	        
	        mReportFile = new File(mReportDirectory, "bug-report.txt");
	    	
	        // we delete previous reports to avoid leaking the info for too long
	        mReportFile.delete();
	        
	    }

	    @Override
	    public void send(CrashReportData errorContent) throws ReportSenderException {

	    	try {
		    	
		    	FileWriter w = new FileWriter(mReportFile);
		    	
		    	w.append(buildBody(errorContent));
		    	
		    	w.close();
	    	} catch (IOException ex) {
	    		Log.e(LOG_TAG, "Error while writing report", ex);
	    		return;
	    	}
	    	
	        final String subject = mContext.getPackageName() + " Crash Report";

	        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
	        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { ACRA.getConfig().mailTo() });
	        emailIntent.setType("text/plain");
	        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
	        
	        ArrayList<Uri> reports = new ArrayList<Uri>();
	        Uri reportUri = FileProvider.getUriForFile(mContext, "ch.carteggio.fileprovider", mReportFile);
			reports.add(reportUri);
	        
	        emailIntent.putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, reports);
	        
	        // we need to grant read access to all apps that can send the report
	        // FIXME : we should be more strict with whom gets to read the log
	        List<ResolveInfo> resInfoList = mContext.getPackageManager().queryIntentActivities(emailIntent, 0);
	        for (ResolveInfo resolveInfo : resInfoList) {
	            String packageName = resolveInfo.activityInfo.packageName;
	            System.out.println("Granting access to " + packageName);
	            mContext.grantUriPermission(packageName, reportUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
	        }
	        
	        mContext.startActivity(emailIntent);
	        
	        
	    }

	    private String buildBody(CrashReportData errorContent) {
	        ReportField[] fields = ACRA.getConfig().customReportContent();
	        if(fields.length == 0) {
	            fields = ACRAConstants.DEFAULT_MAIL_REPORT_FIELDS;
	        }

	        final StringBuilder builder = new StringBuilder();
	        for (ReportField field : fields) {
	            builder.append(field.toString()).append("=");
	            builder.append(errorContent.get(field));
	            builder.append('\n');
	        }
	        return builder.toString();
	    }
}
