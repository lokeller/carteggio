/*******************************************************************************
 * Copyright (c) 2014, Lorenzo Keller
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ch.carteggio;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.os.Handler;
import ch.carteggio.net.ImapMessageStore;
import ch.carteggio.net.NetworkFactories;
import ch.carteggio.net.SmtpMessageTransport;
import ch.carteggio.provider.sync.NotificationService;


@ReportsCrashes(
        formKey = "", // This is required for backward compatibility but not used
        mailTo = "info@carteggio.ch",
        mode =  ReportingInteractionMode.DIALOG,
        resDialogText = R.string.crash_dialog_text,
        resToastText = R.string.crash_toast_text,
        customReportContent = { ReportField.APP_VERSION_CODE, ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL, ReportField.CUSTOM_DATA, ReportField.STACK_TRACE, ReportField.LOGCAT }, 
        logcatArguments = { "-t", "400", "-v", "time"}
)
public class CarteggioApplication extends Application {

	@Override
	public void onCreate() {	
		super.onCreate();
				
		ACRA.init(this);

		ACRA.getErrorReporter().setReportSender(new BugReportSender(this, new Handler()));
		
		NetworkFactories.getInstance(getApplicationContext()).registerStoreFactory("imap", new ImapMessageStore.Factory(getApplicationContext()));
		NetworkFactories.getInstance(getApplicationContext()).registerStoreFactory("imap+ssl+", new ImapMessageStore.Factory(getApplicationContext()));
		NetworkFactories.getInstance(getApplicationContext()).registerStoreFactory("imap+tls+", new ImapMessageStore.Factory(getApplicationContext()));
		
		NetworkFactories.getInstance(getApplicationContext()).registerTransportFactory("smtp", new SmtpMessageTransport.Factory());
		NetworkFactories.getInstance(getApplicationContext()).registerTransportFactory("smtp+ssl+", new SmtpMessageTransport.Factory());
		NetworkFactories.getInstance(getApplicationContext()).registerTransportFactory("smtp+tls+", new SmtpMessageTransport.Factory());
		
		NotificationService.updateUnreadNotification(getApplicationContext());
		
	}

	
}
