package com.genymobile.fridgecheckup;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.*;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * Created by darisk on 16/05/13.
 */
public class WriteTagActivity extends Activity {

    private final static String TAG = WriteTagActivity.class.getSimpleName();
    private final static String TIME_MIMETYPE = "application/vnd.com.geny.fridge";
    // Abbreviate to the max
    private final static String USER_MIMETYPE = "app/vnd.c.geny.f.u";
    private static final String ACCOUNT_NAME = "account";
    private static final int REQUEST_SELECT_ACCOUNT = 0;
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private NfcAdapter nfcAdapter;
    private Tag nfcTag;
    private Ndef ndef;
    private NdefMessage[] readMsgs;
    private PendingIntent dispatchIntent;
    private ImageButton tagBtn;
    private TextView infoTxt;
    private TextView scanTxt;
    private boolean writeMode = false;
    private String accountName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_tag);
	nfcAdapter = NfcAdapter.getDefaultAdapter(this);
	dispatchIntent = PendingIntent.getActivity(this, 0,
		new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	tagBtn = (ImageButton) findViewById(R.id.tagButton);
	infoTxt = (TextView) findViewById(R.id.infoLabel);
	scanTxt = (TextView) findViewById(R.id.scanLabel);
	accountName = getAccountName();
	if (TextUtils.isEmpty(accountName)) {
	    selectAccount();
	}
    }

    @Override
    protected void onStart() {
	super.onStart();
	parseIntent(getIntent());
	updateTagTexts();
    }

    @Override
    protected void onResume() {
	super.onResume();
	Log.i(TAG, "onResume");
	nfcAdapter.enableForegroundDispatch(this,  dispatchIntent, null, null );
    }

    @Override
    protected void onNewIntent(Intent intent) {
	super.onNewIntent(intent);
	Log.i(TAG, "onNewIntent");
	parseIntent(intent);
	setIntent(intent);
	updateTagTexts();
	if (writeMode) {
	    writeTag();
	}
    }

    @Override
    protected void onStop() {
	super.onStop();
	try {
	    if (ndef != null)
	    	ndef.close();
	} catch (IOException e) {
	    Log.e(TAG, "Failed to close nfc tag", e);
	}
	ndef = null;

    }

    private void parseIntent(Intent intent) {
	String action = intent.getAction();
	if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
	    Log.i(TAG, "Ndef NFC TAG discovered " + intent);
	    nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    ndef = Ndef.get(nfcTag);

	    Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
	    if (rawMsgs != null) {
		readMsgs = new NdefMessage[rawMsgs.length];
		for (int i = 0; i < rawMsgs.length; i++) {
		    readMsgs[i] = (NdefMessage) rawMsgs[i];
		}
	    }
	} else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
	    Log.i(TAG, "NFC TAG discovered " + intent);
	    nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
    	} else {
	    Log.d(TAG, "unknown or no NFC TAG " + intent);
	}
    }

    private void updateTagTexts() {
	if (readMsgs != null) {
	    NdefMessage m = readMsgs[0];
	    long time = 0;
	    String username = "unknown user";
	    if (m != null) {
		Charset charset = Charset.forName("US-ASCII");
		for (NdefRecord record : m.getRecords()) {
		    String type = new String(record.getType(), charset);
		    byte[] data = record.getPayload();
		    if (TIME_MIMETYPE.equals(type)) {
			if (data != null && data.length >= 8) {
			    time = byteArrayToLong(data);
			}
		    } else if (USER_MIMETYPE.equals(type)) {
			username = new String(data, charset);
		    }

		}
	    }

	    Log.i(TAG, "Tag was written by " + username + " at " + time);
	    infoTxt.setText("This was added the " + new Date(time) + " by " + username);
	}
    }

    public void onToggleWriteClicked(View v) {
	toggleWriteMode();
    }

    private void toggleWriteMode() {
	writeMode = !writeMode;
	if (writeMode) {
	    scanTxt.setVisibility(View.VISIBLE);
	    infoTxt.setVisibility(View.GONE);
	} else {
	    scanTxt.setVisibility(View.GONE);
	    infoTxt.setVisibility(View.VISIBLE);
	}
    }

    private void writeTag() {
	if (nfcTag == null) {
	    Log.i(TAG, "No nfc tag to write");
	    return;
	}
	Log.i(TAG, "Write nfc tag");
	NdefRecord[] records = new NdefRecord[1];
	Long currentTime = System.currentTimeMillis();
	NdefRecord idRecord = NdefRecord.createMime(TIME_MIMETYPE, longToByteArray(currentTime));
	// records[0] = NdefRecord.createApplicationRecord("com.genymobile.fridgecheckup");
	//TODO use more standard and shorter mimeType
	if (!TextUtils.isEmpty(accountName)) {
	    records[0] = NdefRecord.createMime(USER_MIMETYPE, accountName.getBytes(Charset.forName("US-ASCII")));
	}
//	records[3] = NdefRecord.createMime("application/vnd.com.genymobile.fridgecheckup.useruri", "Future user d+uri".getBytes(Charset.forName("US-ASCII")));
	final NdefMessage message = new NdefMessage(idRecord , records);
	AsyncTask<NdefMessage, Void, Boolean> writeTask = new AsyncTask<NdefMessage, Void, Boolean>() {
	    @Override
	    protected void onPostExecute(Boolean aBoolean) {
		if (aBoolean) {
		    Toast.makeText(WriteTagActivity.this, "Nfc tag writed", Toast.LENGTH_SHORT).show();
		    finish();
		}
		else
		    Toast.makeText(WriteTagActivity.this, "Failed to write Nfc Tag", Toast.LENGTH_SHORT).show();
		//TODO make a confirmation or error sound
		toggleWriteMode();
	    }

	    @Override
	    protected Boolean doInBackground(NdefMessage... ndefMessages) {
		if (ndef == null || !ndef.isWritable()) {
		    Log.d(TAG, "nfc tag is not writable");
		    return false;
		}

		try {
		    Log.i(TAG," writing nfc tag");
		    ndef.connect();
		    ndef.writeNdefMessage(message);
		    return true;
		} catch (IOException e) {
		    Log.e(TAG, "Failed to write nfc tag", e);
		} catch (FormatException e) {
		    Log.e(TAG, "Failed to write nfc tag because of bad format", e);
		} finally {
		    try {
			ndef.close();
		    } catch (IOException e) {
			Log.e(TAG, "Failed to close nfc tag", e);
		    }
		    // ndef = null;
		}
		return false;
	    }
	};
	writeTask.execute(message);
    }

    private byte[] longToByteArray(long l) {
	byte[] bytes = ByteBuffer.allocate(8).putLong(l).array();
	return bytes;
    }

    private long byteArrayToLong(byte[] b) {
	long  res = ByteBuffer.wrap(b).getLong();
	return res;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	if (requestCode == REQUEST_SELECT_ACCOUNT && resultCode == RESULT_OK) {
	    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
	    saveAccount(accountName);
	    this.accountName = accountName;
	} else {

	}

    }

    private void saveAccount(String accountName) {
	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
	sp.edit().putString(ACCOUNT_NAME, accountName).apply();
    }

    private String getAccountName() {
	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
	return sp.getString(ACCOUNT_NAME, "");
    }


    private void selectAccount() {
	Intent intent = AccountManager.newChooseAccountIntent(null, null,
		new String[] {GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null);
	startActivityForResult(intent, REQUEST_SELECT_ACCOUNT);
    }
}
