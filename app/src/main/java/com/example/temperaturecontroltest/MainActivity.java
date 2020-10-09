package com.example.temperaturecontroltest;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.rfid.api3.ACCESS_OPERATION_CODE;
import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.DYNAMIC_POWER_OPTIMIZATION;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.Events;
import com.zebra.rfid.api3.FILTER_ACTION;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.MEMORY_BANK;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.PreFilters;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATE_AWARE_ACTION;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TAG_FIELD;
import com.zebra.rfid.api3.TARGET;
import com.zebra.rfid.api3.TagAccess;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TagStorageSettings;
import com.zebra.rfid.api3.TriggerInfo;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class MainActivity extends AppCompatActivity {



    private static final String TAG = "MainActivity";
    AsyncTask<Void, String, String> task = null;
    AsyncTask<Void, Void, Void> taskPerform = null;
    AsyncTask<Void, Void, Void> taskStop = null;
    Readers readers = null;
    ReaderDevice device = null;
    static RFIDReader reader = null;
    private List<String> data ;
    ScheduledExecutorService scheduler = null;
    ScheduledFuture<?> taskHandler = null;
    boolean isStarted = false;
    static RfidEventHandler eventHandler = null;
    int battery = 0;
    int temperature = 0;
    ProgressDialog progressDialog = null;
    private int powernumber = 297;
    //Ui
    EditText editTextName;
    TextView getTemperatureID;
    IntentFilter filter = new IntentFilter();
    private static final String PROFILE1 = "Sacnner" ;


    Set<RFIDEntity> keys = new HashSet<RFIDEntity>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editTextName = findViewById(R.id.sanID);
        getTemperatureID = findViewById(R.id.tx1);
        filter.addAction(Datawedeentity.ACTION_RESULT_DATAWEDGE);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addAction(Datawedeentity.ACTIVITY_INTENT_FILTER_ACTION);  // The filtered action must match the "Intent action" specified in the DW Profile's Intent output configuration
        setupProgressDialog();
        setupLoadReaderTask();
        setupStatusMonitorTimer();

        String Code128Value = "true";
        String EAN13Value = "false";
        CreateProfile(PROFILE1, Code128Value, EAN13Value);


    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(Datawedeentity.MessageEvent event) {

    };
    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        registerReceiver(Broadcast, filter);

        // Retrieve current active profile using GetActiveProfile: http://techdocs.zebra.com/datawedge/latest/guide/api/getactiveprofile/
        Datawedeentity.sendDataWedgeIntentWithExtra(getApplicationContext(),
                Datawedeentity.ACTION_DATAWEDGE, Datawedeentity. EXTRA_GET_ACTIVE_PROFILE,
                Datawedeentity.EXTRA_EMPTY);
    }
    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        unregisterReceiver(Broadcast);
    }
    private BroadcastReceiver Broadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Datawedeentity.EXTRA_RESULT_GET_ACTIVE_PROFILE)){
                if (intent.hasExtra(Datawedeentity.EXTRA_RESULT_GET_ACTIVE_PROFILE)) {
                    String activeProfile = intent.getStringExtra(Datawedeentity.EXTRA_RESULT_GET_ACTIVE_PROFILE);
                    EventBus.getDefault().post(new Datawedeentity.MessageEvent(activeProfile));
                }

            }


            if (action.equals(Datawedeentity.ACTIVITY_INTENT_FILTER_ACTION)) {
                //  Received a barcode scan
                try {

                    displayScanResult(intent, "via Broadcast");
                } catch (Exception e) {
                    //  Catch if the UI does not exist when we receive the broadcast
                    Toast.makeText(getApplicationContext(), "Error; " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

        }
    };

    private String readTagId(MEMORY_BANK mb,String tagnewId,int iWordOffset, int iWordCount){
        //String tagnewId = textold.getText().toString();
        TagAccess tagAccess = new TagAccess();
        TagAccess.ReadAccessParams readAccessParams = tagAccess.new ReadAccessParams();
        TagData readAcessTag;
        readAccessParams.setAccessPassword(0);
//        readAccessParams.setCount(3);
//        readAccessParams.setOffset(2);
        readAccessParams.setOffset(iWordOffset);
        readAccessParams.setCount(iWordCount);


        // readAccessParams.setMemoryBank(MEMORY_BANK.MEMORY_BANK_USER);
        readAccessParams.setMemoryBank(mb);
        try {
            readAcessTag = reader.Actions.TagAccess.readWait("201905143989201905143989", readAccessParams,null);
//            editid.setText(readAcessTag.getMemoryBankData().toString());
           // Log.d(TAG,"READTAg:" + readAcessTag.getMemoryBankData().toString());

            return readAcessTag.getMemoryBankData();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
        return null;

    }
    String T_test = "0000";
    String T ;

    private String getTeCount(String tagId){
        do {
            T = readTagId(MEMORY_BANK.MEMORY_BANK_USER,"0000000000"+tagId, 31, 1);

            T_test = readTagId(MEMORY_BANK.MEMORY_BANK_RESERVED, "0000000000"+tagId,8,1);
            Log.d("fg",T_test);
        }while (T_test !="0000");

        return null;
    }


    TimerTask taskread = new TimerTask() {
        @Override
        public void run() {
            // task to run goes here
            // 执行的输出的内容
            if (getTemperatureID.getText().toString().equals("")){
               //stopReadTemperature();
                Log.d("fg","aaaaaaa");
                bEPC(keys);
            }


        }
    };
    // Display scanned data
    private void displayScanResult(Intent initiatingIntent, String howDataReceived)
    {
        //Id
        String decodedData = initiatingIntent.getStringExtra(Datawedeentity.DATAWEDGE_INTENT_KEY_DATA);
        //类型
        String decodedDecoder = initiatingIntent.getStringExtra(Datawedeentity.DATAWEDGE_INTENT_KEY_DECODER);
        editTextName.setText(decodedData);
        if (decodedDecoder != null) {
            Toast.makeText(getApplicationContext(),"开始读取",Toast.LENGTH_SHORT).show();
            getTemperatureID.setText("");
            //keys.clear();
            new StartReadTemperature(decodedData).execute();
        }
//        setupProgressDialog();
//        setupLoadReaderTask();
//        setupStatusMonitorTimer();
    }

    private void CreateProfile (String profileName, String code128Value, String ean13Value){

        // Configure profile to apply to this app
        Bundle bMain = new Bundle();
        bMain.putString("PROFILE_NAME", profileName);
        bMain.putString("PROFILE_ENABLED", "true");
        bMain.putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST");  // Create profile if it does not exist

        // Configure barcode input plugin
        Bundle bConfigBarcode = new Bundle();
        bConfigBarcode.putString("PLUGIN_NAME", "BARCODE");
        bConfigBarcode.putString("RESET_CONFIG", "true"); //  This is the default

        // PARAM_LIST bundle properties
        Bundle bParamsBarcode = new Bundle();
        bParamsBarcode.putString("scanner_selection", "auto");
        bParamsBarcode.putString("scanner_input_enabled", "true");
        bParamsBarcode.putString("decoder_code128", code128Value);
        bParamsBarcode.putString("decoder_ean13", ean13Value);

        // Bundle "bParamsBarcode" within bundle "bConfigBarcode"
        bConfigBarcode.putBundle("PARAM_LIST", bParamsBarcode);

        // Associate appropriate activity to profile
        String activityName = new String();
        Bundle appConfig = new Bundle();
        appConfig.putString("PACKAGE_NAME", getPackageName());
        if (profileName.equals(PROFILE1))
        {
            activityName = MainActivity.class.getSimpleName();
        }
        String activityPackageName = getPackageName() + "." + activityName;
        appConfig.putStringArray("ACTIVITY_LIST", new String[] {activityPackageName});
        bMain.putParcelableArray("APP_LIST", new Bundle[]{appConfig});

        // Configure intent output for captured data to be sent to this app
        Bundle bConfigIntent = new Bundle();
        bConfigIntent.putString("PLUGIN_NAME", "INTENT");
        bConfigIntent.putString("RESET_CONFIG", "true");

        // Set params for intent output
        Bundle bParamsIntent = new Bundle();
        bParamsIntent.putString("intent_output_enabled", "true");
        bParamsIntent.putString("intent_action", Datawedeentity.ACTIVITY_INTENT_FILTER_ACTION);
        bParamsIntent.putString("intent_delivery", "2");

        // Bundle "bParamsIntent" within bundle "bConfigIntent"
        bConfigIntent.putBundle("PARAM_LIST", bParamsIntent);

        // Place both "bConfigBarcode" and "bConfigIntent" bundles into arraylist bundle
        ArrayList<Bundle> bundlePluginConfig = new ArrayList<>();
        bundlePluginConfig.add(bConfigBarcode);
        bundlePluginConfig.add(bConfigIntent);

        // Place bundle arraylist into "bMain" bundle
        bMain.putParcelableArrayList("PLUGIN_CONFIG", bundlePluginConfig);

        // Apply configs using SET_CONFIG: http://techdocs.zebra.com/datawedge/latest/guide/api/setconfig/
        Datawedeentity.sendDataWedgeIntentWithExtra(getApplicationContext(),
                Datawedeentity.ACTION_DATAWEDGE, Datawedeentity.EXTRA_SET_CONFIG, bMain);

       // Toast.makeText(getApplicationContext(), "Created profiles.  Check DataWedge app UI.", Toast.LENGTH_LONG).show();
    }

    private void getText() throws InvalidUsageException, OperationFailureException {
        reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.BARCODE_MODE,true);
        Antennas.AntennaRfConfig config = null;
        config = reader.Config.Antennas.getAntennaRfConfig(1);
        config.setTransmitPowerIndex(270);
        config.setrfModeTableIndex(0);
        config.setTari(0);
        reader.Config.Antennas.setAntennaRfConfig(1, config);
    }

    //连接信息
    private void setupProgressDialog() {
        if (progressDialog == null)
            progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.connecting));
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
    }

    private void setupStatusMonitorTimer() {
        if (scheduler != null) return;
        scheduler = Executors.newScheduledThreadPool(1);
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    if (reader != null) {
                        reader.Config.getDeviceStatus(true, true, true);
                    } else {
                        scheduler.shutdown();
                    }
                } catch (InvalidUsageException | OperationFailureException e) {
                    if (e instanceof OperationFailureException) {
                        Log.d(TAG, "OperationFailureException: " + ((OperationFailureException) e).getVendorMessage());
                    }
                    e.printStackTrace();
                }
            }
        };
        taskHandler = scheduler.scheduleAtFixedRate(task, 10, 60, SECONDS);
    }

    @SuppressLint("StaticFieldLeak")
    private void setupLoadReaderTask() {
        if (task != null) task.cancel(true);

        if (readers == null) readers = new Readers(this,ENUM_TRANSPORT.SERVICE_SERIAL);
        if (!progressDialog.isShowing()) progressDialog.show();
        task = new AsyncTask<Void, String, String>() {
            @Override
            protected synchronized String doInBackground(Void... voids) {
                if (isCancelled()) return null;
                if (readers == null) return null;
                publishProgress("readers.GetAvailableRFIDReaderList()");
                if (isCancelled()) return null;
                List<ReaderDevice> list = null;
                try {
                    list = readers.GetAvailableRFIDReaderList();
                } catch (InvalidUsageException e) {
                    e.printStackTrace();
                }
                if (list == null || list.isEmpty()) return null;
                publishProgress("device.getRFIDReader()");
                if (isCancelled()) return null;
                for (ReaderDevice readerDevice : list) {
                    device = readerDevice;
                    // Log.d("setupLoadReaderTask", device.getName());
                    reader = device.getRFIDReader();
                    if (reader.isConnected()) return null;
                    publishProgress("reader.connect()");
                    if (isCancelled()) return null;
                    try {
                        reader.connect();
                        configureReader();
                    } catch (InvalidUsageException | OperationFailureException e) {
                        e.printStackTrace();
                    }
                    if (reader.isConnected()) break;
                }
                if (!reader.isConnected()) return null;
                return String.format("Connected to %s", device.getName());
            }

            @Override
            protected void onProgressUpdate(String... values) {
                // if (values.length == 0) return;
                // String s = null;
                // for (String value : values) s = value;
                // Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
            }

            @Override
            protected void onPostExecute(String s) {
                if (s == null) {
                    setupRetryDialog();
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            protected void onCancelled() {
                reader = null;
                readers = null;
                Toast.makeText(getApplicationContext(), "Connection Cancelled", Toast.LENGTH_SHORT).show();
            }
        };
        task.execute();
    }

    //RFD设备设置
    private void configureReader()  {
        if (reader == null || !reader.isConnected()) return;
        TriggerInfo triggerInfo = new TriggerInfo();
        triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
        triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
        if (eventHandler == null) eventHandler = new RfidEventHandler();
        try {
//            reader.Events.addEventsListener(eventHandler);
//            reader.Events.setHandheldEvent(true);
//            reader.Events.setTagReadEvent(false);
//            reader.Events.setBatteryEvent(true);
//            reader.Events.setPowerEvent(true);
//            reader.Events.setTemperatureAlarmEvent(true);
//            reader.Events.setAttachTagDataWithReadEvent(false);
            reader.Config.setStartTrigger(triggerInfo.StartTrigger);
            reader.Config.setStopTrigger(triggerInfo.StopTrigger);
            reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.BARCODE_MODE,true);
            Antennas.AntennaRfConfig config = null;
            config = reader.Config.Antennas.getAntennaRfConfig(1);
            config.setTransmitPowerIndex(297);
            config.setrfModeTableIndex(0);
            config.setTari(0);
            reader.Config.Antennas.setAntennaRfConfig(1, config);
            Antennas.SingulationControl control = reader.Config.Antennas.getSingulationControl(1);
            control.setSession(SESSION.SESSION_S0);
            control.setTagPopulation((short) 30);
            control.Action.setSLFlag(SL_FLAG.SL_ALL);
            control.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
            reader.Config.Antennas.setSingulationControl(1, control);

            TagStorageSettings tagStorageSettings = reader.Config.getTagStorageSettings();
            // set tag storage settings on the reader with all fields
            tagStorageSettings.setTagFields(TAG_FIELD.ALL_TAG_FIELDS);
            reader.Config.setTagStorageSettings(tagStorageSettings);

            // set Dynamic power state off
            reader.Config.setDPOState(DYNAMIC_POWER_OPTIMIZATION.DISABLE);
            Log.d("TEST", "registerEventAndStatusNotify: ");
           // reader.Actions.PreFilters.deleteAll();
            registerEventAndStatusNotify();

        } catch (InvalidUsageException | OperationFailureException e) {
            e.printStackTrace();
        } // Log.d("OperationFailureException", e.getVendorMessage());


    }

    boolean bResult;
    public boolean addOperationReadSequence(MEMORY_BANK mb, int iWordOffset, int iWordCount) {
        bResult = false;
        TagAccess tagAccess = new TagAccess();
        TagAccess.Sequence sequence = tagAccess.new Sequence(tagAccess);
        TagAccess.Sequence.Operation operation = sequence.new Operation();
        operation.setAccessOperationCode(ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ);
        operation.ReadAccessParams.setMemoryBank(mb);
        operation.ReadAccessParams.setAccessPassword(0);
        operation.ReadAccessParams.setCount(iWordCount);
        operation.ReadAccessParams.setOffset(iWordOffset);

        try {
            reader.Actions.TagAccess.OperationSequence.add(operation);
            bResult = true;
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            Log.d("TEST", "OperationSequenceAdd Exception=" + e.getMessage());
        } catch (OperationFailureException e) {
            e.printStackTrace();
            Log.d("TEST", "OperationSequenceAdd Exception=" + e.getMessage());
        }
        return bResult;
    }

    public byte[] hexStringtoBytes(String str) {
        if (str == null || str.trim().equals("")) {
            return new byte[0];
        }

        byte[] bytes = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }

        return bytes;
    }
    //添加过滤器
    public void addPrefilter(String tagId) {
        PreFilters filters = new PreFilters();
        PreFilters.PreFilter filter = filters.new PreFilter();
        //byte[] tagPattern = hexStringtoBytes(tagId);
         byte[] tagPattern = new byte[] { 0x20, 0x18, 0x10, 0x12, 0x09, 0x48 };
        filter.setAntennaID((short) 1);// Set this filter for Antenna ID 1
        filter.setTagPattern(tagPattern);// Tags which starts with 0x1211
        filter.setTagPatternBitCount(tagPattern.length * 8);
        filter.setBitOffset(80); // skip PC bits (always it should be in bit length)
        filter.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC);
        filter.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE);        // use state aware singulation
        filter.StateAwareAction.setTarget(TARGET.TARGET_INVENTORIED_STATE_S1);  // inventoried flag of session S1 of
        // matching tags to B
        filter.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_B_NOT_INV_A);
        // not to
        // select tags that match the criteria
        // not to select tags that match the criteria
        try {
            Log.d("TEST", "addPrefilter: " + tagId);
            reader.Actions.PreFilters.add(filter);
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            Log.d("TEST", "OperationSequencePerform Exception=" + e.getMessage());
        } catch (OperationFailureException e) {
            e.printStackTrace();
            Log.d("TEST", "OperationSequencePerform Exception=" + e.getMessage());
        }
    }
    private static boolean inTemperatureReading = false;

    public class StartReadTemperature extends AsyncTask<Void, Void, Void> {
        private String mTagId;
        public StartReadTemperature(String tagId) {
            mTagId = tagId;
        }
        @Override
        protected void onPreExecute() {
            //Toast.makeText(getApplicationContext(), "开始读取温度!", Toast.LENGTH_LONG).show();
        }
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                reader.Actions.purgeTags();

                // Add prefilter
                if (reader.Actions.PreFilters.length() > 0) reader.Actions.PreFilters.deleteAll();

                addPrefilter(mTagId);

                //rfidReader.Actions.TagAccess.OperationSequence.stopSequence();
                if (reader.Actions.TagAccess.OperationSequence.getLength() > 0) reader.Actions.TagAccess.OperationSequence.deleteAll();

                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_EPC,5,3);
                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_USER, 31, 1);
                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
//                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
//                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
//                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
//                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);
//                addOperationReadSequence(MEMORY_BANK.MEMORY_BANK_RESERVED, 8, 1);

                Log.d("TEST", "Start sequence read operation ...");
                reader.Actions.TagAccess.OperationSequence.performSequence();
                inTemperatureReading = true;
            } catch (InvalidUsageException e) {
                e.printStackTrace();
                Log.d("TEST", "OperationSequencePerform Exception=" + e.getMessage());
            } catch (OperationFailureException e) {
                e.printStackTrace();
                Log.d("TEST", "OperationSequencePerform Exception=" + e.getMessage());
            }
            return null;
        }
    }


    private void setupRetryDialog() {
        if (progressDialog.isShowing()) progressDialog.dismiss();
        new AlertDialog.Builder(this)
                .setTitle(R.string.err_title)
                .setMessage(R.string.retry)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setupLoadReaderTask();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }



    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (task != null) task.cancel(true);
        if (reader != null) {
            task = new AsyncTask<Void, String, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    try {
                        reader.disconnect();
                    } catch (InvalidUsageException | OperationFailureException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
            task.execute();
        }
        if (readers != null) {
            readers.Dispose();
        }
    }




    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("确认退出吗？")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (eventHandler != null) {
                            try {
                                reader.Events.removeEventsListener(eventHandler);
                            } catch (InvalidUsageException e) {
                                e.printStackTrace();
                            } catch (OperationFailureException e) {
                                Log.d(TAG, "onBackPressed: " + e.getVendorMessage());
                                e.printStackTrace();
                            }
                            eventHandler = null;
                        }
                        finish();

                    }
                })
                .setNegativeButton("返回", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 点击“返回”后的操作,这里不设置没有任何操作
                    }
                }).show();

    }



    public void bEPC(Set<RFIDEntity> keys) {
        if(getTemperatureID.getText().toString().equals("")&&keys.isEmpty()){
            boolean isEmpty = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),editTextName.getText().toString()+"标签不存在",Toast.LENGTH_SHORT).show();

                }
            });
        }
    }
    private void init (TagData[] tags){
        RFIDEntity rf = new RFIDEntity();
        rf.setEPC(tags[0].getMemoryBankData());
        keys.add(rf);
    }

    private class RfidEventHandler implements RfidEventsListener {
        @Override
        public void eventReadNotify(RfidReadEvents rfidReadEvents) {
            final TagData[] tags = reader.Actions.getReadTags(10);
            //if(tags != null) init(tags);


           if (tags != null && editTextName.getText().toString().equals(tags[0].getMemoryBankData())) {
                if (tags.length >= 5) {
                    Log.d("TEST", "tags lengths: " + tags.length);
                    for (int index = 0; index < tags.length; index++) {
                        Log.d("TEST", "/MB: " + tags[index].getMemoryBank() + " /DATA: " + tags[index].getMemoryBankData());
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("TEST", "run: ");
                            Log.d("TEST", "tag[4]: " + tags[4].getMemoryBankData());
                            if (tags[4].getMemoryBankData() == null || tags[4].getMemoryBankData() == "0000")
                                return;
                            Integer N = Integer.parseInt(tags[4].getMemoryBankData(), 16) & 0x0fff;
                            if (N == 0) return;
                            Log.d("TEST", "N: " + N.toString());
                            String s = tags[1].getMemoryBankData();
                            Log.d("TEST", "s: " + s);
                            String t = s.substring(2, 3);
                            Log.d("TEST", "t: " + t);
                            Integer TUNE = 0;
                            if (s.startsWith("00")) {
                                TUNE = Integer.parseInt(t, 16);
                            } else {
                                TUNE = Integer.parseInt(t, 16) * (-1);
                            }
                            Log.d("TEST", "TUNE: " + TUNE.toString());
                            // T = (N + TUNE − 500)/5.4817 + 24.9
                            double T = (N + TUNE - 500) / 5.4817 + 24.9;
                            Log.d("TEST", "T: " + String.valueOf(T));
                            DecimalFormat df = new DecimalFormat("###0.0" + "℃");
                            getTemperatureID.setText(df.format(T));
                            stopReadTemperature();
                        }
                    });

                }

            }
            //if (tags == null) return;


        }



        boolean readTemperature = false;
        @SuppressLint("StaticFieldLeak")
        @Override
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            Events.StatusEventData data = rfidStatusEvents.StatusEventData;
            STATUS_EVENT_TYPE type = data.getStatusEventType();
            Log.d("STATUS", type.toString());
            if (type == STATUS_EVENT_TYPE.BATTERY_EVENT) {
                battery = data.BatteryData.getLevel();
                // Log.d(TAG,"电量："+battery );
            } else if (type == STATUS_EVENT_TYPE.TEMPERATURE_ALARM_EVENT) {
                temperature = data.TemperatureAlarmData.getCurrentTemperature();
            } else if (type == STATUS_EVENT_TYPE.POWER_EVENT){
                float io =  data.PowerData.getPower();

                //Log.d("Power", String.valueOf(io));

            } else if (type == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                HANDHELD_TRIGGER_EVENT_TYPE eventType = data.HandheldTriggerEventData.getHandheldEvent();
                if (eventType == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                    if (taskPerform != null) taskPerform.cancel(true);
                    taskPerform = new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            if (inTemperatureReading == true) stopReadTemperature();
                           // getTeCount("decodedDecoder");
//                            try {
////                                reader.Actions.Inventory.perform();
////                               // reader.Actions.purgeTags();
////                            } catch (InvalidUsageException | OperationFailureException e) {
////                               // triggerStop();
////                                e.printStackTrace();
////                            }
                            return null;
                        }
                    };
                    taskPerform.execute();
                } else if (eventType == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                    if (taskStop != null) taskStop.cancel(true);
                    taskStop = new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {

                          //  triggerStop();
                            return null;
                        }
                    };
                    taskStop.execute();
                }
            }
        }

    }

    private void registerEventAndStatusNotify() throws InvalidUsageException, OperationFailureException {
        reader.Events.addEventsListener(eventHandler);

        // Subscribe required status notification
        reader.Events.setInventoryStartEvent(true);
        reader.Events.setInventoryStopEvent(true);

        // enables tag read notification. if this is set to false, no tag read notification is send
        reader.Events.setTagReadEvent(true);

        reader.Events.setReaderDisconnectEvent(true);
        reader.Events.setBatteryEvent(true);
        reader.Events.setHandheldEvent(true);
    }

    public void stopReadTemperature() {
        try {
            reader.Actions.purgeTags();
            //rfidReader.Actions.Inventory.perform();
            if (reader.Actions.TagAccess.OperationSequence.getLength() > 0)
                reader.Actions.TagAccess.OperationSequence.deleteAll();

            Log.d("TEST", "Stop sequence read operation ...");
            inTemperatureReading = false;
            reader.Actions.TagAccess.OperationSequence.stopSequence();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            Log.d("TEST", "OperationSequencePerform Exception=" + e.getMessage());
        } catch (OperationFailureException e) {
            e.printStackTrace();
            Log.d("TEST", "OperationSequencePerform Exception=" + e.getMessage());
        }
    }
}