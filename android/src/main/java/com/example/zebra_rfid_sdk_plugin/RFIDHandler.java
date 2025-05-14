package com.example.zebra_rfid_sdk_plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.zebra.rfid.api3.ACCESS_OPERATION_CODE;
import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TriggerInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel.Result;


public class RFIDHandler implements Readers.RFIDReaderEventHandler {
    private String TAG = "RFIDHandler";
    Context context;

    public Handler mEventHandler = new Handler(Looper.getMainLooper());
    private AsyncTask<Void, Void, String> AutoConnectDeviceTask;
    private static Readers readers;
    //    private static ArrayList<ReaderDevice> availableRFIDReaderList;
    private static ReaderDevice readerDevice;
    private static RFIDReader reader;
    private int MAX_POWER = 270;
    private IEventHandler eventHandler = new IEventHandler();
    private Function<String, Map<String, Object>> _emit;
    private EventChannel.EventSink sink = null;
    Antennas.AntennaRfConfig config ;



    public int getMaxPower() {
        return MAX_POWER;
    }

    public void setMaxPower(int power) {
        this.MAX_POWER = power;
        // Eğer reader bağlıysa mevcut konfigürasyonu güncelle
        if (isReaderConnected()) {
            try {
                config.setTransmitPowerIndex(MAX_POWER);
                reader.Config.Antennas.setAntennaRfConfig(1, config);
            } catch (InvalidUsageException | OperationFailureException e) {
                e.printStackTrace();
            }
        }
    }


    private void emit(final String eventName, final HashMap map) {
        map.put("eventName", eventName);
        mEventHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sink != null) {
                    sink.success(map);
                }
            }
        });
    }

    RFIDHandler(Context _context) {
        context = _context;
   
    }

    public void setEventSink(EventChannel.EventSink _sink){
        sink = _sink;
    }

    @SuppressLint("StaticFieldLeak")
    public void connect(final Result result) {
        Readers.attach(this);
        if (readers == null) {
            readers = new Readers(context, ENUM_TRANSPORT.ALL);
        }
        AutoConnectDevice(result);
    }
    public void dispose() {
        try {
            if (reader != null && reader.isConnected()) {
                try {
                    reader.disconnect();
                    Log.d(TAG, "RFID Reader disconnected");
                } catch (InvalidUsageException | OperationFailureException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error during disconnect: " + e.getMessage());
                }
            }
            if (readers != null) {
                readers.Dispose();
                readers = null;
            }
            reader = null;
            readerDevice = null;

            HashMap<String, Object> map = new HashMap<>();
            map.put("status", Base.ConnectionStatus.UnConnection.ordinal());
            emit(Base.RfidEngineEvents.ConnectionStatus, map);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Dispose error: " + e.getMessage());
        }
    }



    @SuppressLint("StaticFieldLeak")
    public void AutoConnectDevice(final Result result) {
        AutoConnectDeviceTask = new AsyncTask<Void, Void, String>() {

            ArrayList<ReaderDevice> readersListArray;

            @Override
            protected String doInBackground(Void... voids) {
                Log.d(TAG, "CreateInstanceTask");
                try {

                    if (readerDevice == null) {
                        readersListArray = readers.GetAvailableRFIDReaderList();

                        for (ReaderDevice device : readersListArray) {
                            // Örneğin device.toString() kullanarak ya da device.getName() gibi metotlarla
                            Log.d(TAG, "Bulunan ReaderDevice: " + device.toString());
                        }

                        if (readersListArray.size() > 0) {
                            readerDevice = readersListArray.get(0);
                            reader = readerDevice.getRFIDReader();
                        } 
                        else {
                            //return "No connectable device detected";
                            return "";
                        }
                    }

                    if (reader != null && !reader.isConnected() && !this.isCancelled()) {
                        reader.connect();
                        ConfigureReader();
                    }

                } catch (InvalidUsageException ex) {
                    Log.d(TAG, "InvalidUsageException");
                    return ex.getMessage();

//                    exceptionIN = ex;
                } catch (OperationFailureException e) {
                    String details = e.getStatusDescription();
                    String a= e.getVendorMessage();
                    return details;
//                    exception = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(String error) {
                Base.ConnectionStatus status=Base.ConnectionStatus.ConnectionRealy;
                super.onPostExecute(error);
                if (error != null) {
                    emit(Base.RfidEngineEvents.Error, transitionEntity(Base.ErrorResult.error(error)));
                    status=Base.ConnectionStatus.ConnectionError;
                }
                HashMap<String, Object> map =new HashMap<>();
                map.put("status",status.ordinal());
                emit(Base.RfidEngineEvents.ConnectionStatus,map);



            if (error == null && readersListArray != null) {
                ArrayList<Map<String, Object>> deviceList = new ArrayList<>();
                for (ReaderDevice device : readersListArray) {
                    Map<String, Object> deviceMap = new HashMap<>();
                    deviceMap.put("name", device.getName());
                    deviceMap.put("address", device.getAddress());
                    deviceList.add(deviceMap);
                }

                result.success(deviceList); 
            } else {
                // Hata oluşmuşsa ya da liste yoksa (ya da zaten hata durumunu ilettiysek),
                // boş dönebiliriz veya özel bir hata metni gönderebiliriz.
                result.success(new ArrayList<>()); 
            }





            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                AutoConnectDeviceTask = null;
            }

        }.execute();


    }



    public boolean writeEpcToTag(String targetEpc, String newEpc) {
    if (!isReaderConnected()) {
        Log.e(TAG, "RFID Reader not connected.");
        return false;
    }

    try {
        // EPC yazımı için filtre oluşturuluyor
        TagFilter filter = new TagFilter();
        filter.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC);
        filter.setTagPattern(targetEpc);
        filter.setBitOffset(32); // EPC belleği başlangıç offset’i
        filter.setTagPatternBitCount(targetEpc.length() * 4); // 1 hex karakter = 4 bit
        filter.setFilterMatch(true); // Eşleşen etiketleri seç

        // Yazılacak yeni EPC verisi
        TagData tagData = new TagData(newEpc);

        // EPC yazım işlemi
        reader.Actions.TagAccess.writeWait(tagData, MEMORY_BANK.MEMORY_BANK_EPC, 2, 0, filter);

        Log.i(TAG, "Yeni EPC başarıyla yazıldı: " + newEpc);
        return true;

    } catch (InvalidUsageException | OperationFailureException e) {
        Log.e(TAG, "EPC yazım hatası: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}

    private boolean isReaderConnected() {
        if (reader != null && reader.isConnected())
            return true;
        else {
            Log.d(TAG, "reader is not connected");
            return false;
        }
    }

    private synchronized void ConfigureReader() {
        if (reader == null) {
            Log.e(TAG, "RFIDReader is null! ConfigureReader aborted.");
            return;
        }

        try {
            Log.d(TAG, "ConfigureReader " + reader.getHostName());

            if (reader.isConnected()) {
                TriggerInfo triggerInfo = new TriggerInfo();
                triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
                triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);

                this.config = reader.Config.Antennas.getAntennaRfConfig(1);

                // receive events from reader
                reader.Events.addEventsListener(eventHandler);
                reader.Events.setHandheldEvent(true);
                reader.Events.setTagReadEvent(true);
                reader.Events.setAttachTagDataWithReadEvent(false);

                // set trigger mode as rfid so scanner beam will not come
                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true);

                // set start and stop triggers
                reader.Config.setStartTrigger(triggerInfo.StartTrigger);
                reader.Config.setStopTrigger(triggerInfo.StopTrigger);

                // power levels are index based so maximum power supported get the last one
                MAX_POWER = reader.ReaderCapabilities.getTransmitPowerLevelValues().length - 1;

                // set antenna configurations
                config.setTransmitPowerIndex(MAX_POWER);
                config.setrfModeTableIndex(0);
                config.setTari(0);
                reader.Config.Antennas.setAntennaRfConfig(1, config);

                // Set the singulation control
                Antennas.SingulationControl s1_singulationControl = reader.Config.Antennas.getSingulationControl(1);
                s1_singulationControl.setSession(SESSION.SESSION_S0);
                s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
                s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL);
                reader.Config.Antennas.setSingulationControl(1, s1_singulationControl);

                // delete any prefilters
                reader.Actions.PreFilters.deleteAll();
            }
        } catch (InvalidUsageException | OperationFailureException | NullPointerException e) {
            Log.e(TAG, "ConfigureReader failed: " + e.getMessage(), e);
        }
    }

    ///Get reader information
    public   HashMap getReadersList() {
        HashMap<String,String> readersList=new  HashMap();
        try {
            if (readers == null) {
                readers = new Readers(context, ENUM_TRANSPORT.ALL);
            }

            if(readers!=null) {
                ArrayList<ReaderDevice> readersListArray = readers.GetAvailableRFIDReaderList();
                for (ReaderDevice readerDevice : readersListArray) {
                    Log.d(TAG, "Reader Name " + readerDevice.getName());
                    Log.d(TAG, "Reader Model " + readerDevice.getAddress());
                    readersList.put(readerDevice.getName(),readerDevice.getAddress());
                }
                return readersList;
            }
        }catch (InvalidUsageException e){
            //            emit(Base.RfidEngineEvents.Error, transitionEntity(Base.ErrorResult.error(error)));
                        }
        return  readersList;
    }

/*
    public void handleGetReadersList(final Result result) {
        ArrayList<ReaderDevice> readersList = getReadersList();

        ArrayList<Map<String, String>> listToReturn = new ArrayList<>();
        for (ReaderDevice device : readersList) {
            Map<String, String> map = new HashMap<>();
            map.put("name", device.getName());
            map.put("address", device.getAddress());
            listToReturn.add(map);
        }
        result.success(listToReturn);
    }
    */




    public class IEventHandler implements RfidEventsListener {
        @Override
        public void eventReadNotify(RfidReadEvents rfidReadEvents) {
            // Recommended to use new method getReadTagsEx for better performance in case of large tag population
            TagData[] myTags = reader.Actions.getReadTags(100);


            if (myTags != null) {
                ArrayList<HashMap<String, Object>> datas= new ArrayList<>();
                for (int index = 0; index < myTags.length; index++) {
                    TagData tagData=myTags[index];
                    Log.d(TAG, "Tag ID " +tagData.getTagID());
                    Log.d(TAG, "Tag getOpCode " +tagData.getOpCode());
                    Log.d(TAG, "Tag getOpStatus " +tagData.getOpStatus());

                    ///read operation
                    if(tagData.getOpCode()==null || tagData.getOpCode()== ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ){
                        //&&tagData.getOpStatus()== ACCESS_OPERATION_STATUS.ACCESS_SUCCESS
                        Base.RfidData data=new Base.RfidData();
                        data.tagID=tagData.getTagID();
                        data.antennaID=tagData.getAntennaID();
                        data.peakRSSI=tagData.getPeakRSSI();
                        data.opStatus=tagData.getOpStatus();
                        data.allocatedSize=tagData.getTagIDAllocatedSize();
                        data.lockData=tagData.getPermaLockData();
                        if(tagData.isContainsLocationInfo()){
                            data.relativeDistance=tagData.LocationInfo.getRelativeDistance();
                        }
                        data.memoryBankData=tagData.getMemoryBankData();
                        datas.add(transitionEntity(data) );
                    }
                }

                if(datas.size()>0){
                    new AsyncDataNotify().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, datas);
                }
            }
        }

        @Override
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED)
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            handleTriggerPress(true);
                            return null;
                        }
                    }.execute();
            }
            if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        handleTriggerPress(false);
                        return null;
                    }
                }.execute();
            }
        }


    }




    public void handleTriggerPress(boolean pressed) {
        if (pressed) {
            performInventory();
        } else
            stopInventory();
    }


    synchronized void performInventory() {
        // check reader connection
        if (!isReaderConnected())
            return;
        try {
            reader.Actions.Inventory.perform();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
    }

    synchronized void stopInventory() {
        // check reader connection
        if (!isReaderConnected())
            return;
        try {
            reader.Actions.Inventory.stop();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void RFIDReaderAppeared(ReaderDevice readerDevice) {
        Log.d(TAG, "RFIDReaderAppeared " + readerDevice.getName());
//        new ConnectionTask().execute();
    }

    @Override
    public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
        Log.d(TAG, "RFIDReaderDisappeared " + readerDevice.getName());
//        if (readerDevice.getName().equals(reader.getHostName()))
//            disconnect();
        dispose();

    }

    private  class AsyncDataNotify extends AsyncTask<ArrayList<HashMap<String, Object>>, Void, Void> {
        @Override
        protected Void doInBackground(ArrayList<HashMap<String, Object>>... params) {
            HashMap<String,Object> hashMap=new HashMap<>();
            hashMap.put("datas",params[0]);
            emit(Base.RfidEngineEvents.ReadRfid,hashMap);
            return null;
        }
    }


    //Entity class transfer HashMap
    public static HashMap<String, Object> transitionEntity(Object onClass) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        Field[] fields = onClass.getClass().getDeclaredFields();
        for (Field field : fields) {
            //Make private variables accessible during reflection
            field.setAccessible(true);
            try {
                hashMap.put(field.getName(), field.get(onClass));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return hashMap;
    }
}
