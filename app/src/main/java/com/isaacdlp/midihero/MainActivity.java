package com.isaacdlp.midihero;

import android.annotation.SuppressLint;

import android.inputmethodservice.Keyboard;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static MyInputMethodService input = null;

    private String[] instruments = {
            "Drums",
            "Guitar",
            "Piano"
    };
    private int instrument = 0;

    private boolean navMode = false;

    private Spinner spinOutput;
    private Spinner spinInstr;
    private Button btnRefresh;
    private Button btnStart;
    private Button btnStop;
    private TextView textView;

    private MidiManager midiManager;
    private MidiOutputPort midiOutputPort;
    private MidiDeviceInfo selectedOutputDevice;
    private ArrayList<MidiDeviceInfo> midiDevices = new ArrayList<>();


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        midiManager = (MidiManager) getSystemService(MIDI_SERVICE);

        // Initialize UI elements
        spinOutput = findViewById(R.id.spinnerOutput);
        spinInstr = findViewById(R.id.spinnerInstr);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        textView = findViewById(R.id.textView);

        ArrayAdapter<String> adapterOUT = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapterOUT.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapterOUT.add("Select instrument");
        for (int i = 0; i < instruments.length; i++) {
            adapterOUT.add(instruments[i]);
        }
        spinInstr.setAdapter(adapterOUT);

        // Set listeners for Spinner selections
        spinOutput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    MidiDeviceInfo device = midiDevices.get(position - 1);
                    selectedOutputDevice = device;
                    textView.setText("Input selected: " + device.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME));
                } else {
                    selectedOutputDevice = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textView.setText("No input selected");
            }
        });

        spinInstr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                instrument = position;
                if (position > 0) {
                    textView.setText("Instrument selected: " + instruments[position - 1]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textView.setText("No instrument selected");
            }
        });

        // Set listeners for Buttons
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDeviceLists();
                textView.setText("Please select MIDI input and instrument");
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMidi();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMidi();
            }
        });

        refreshDeviceLists();
    }

    private void refreshDeviceLists() {
        if (midiManager == null) {
            textView.setText("Cannot find the MIDI Manager");
            return;
        }

        stopMidi();

        selectedOutputDevice = null;

        midiDevices.clear();
        MidiDeviceInfo[] devices = midiManager.getDevices();
        for (MidiDeviceInfo device : devices) {
            midiDevices.add(device);
        }

        ArrayAdapter<String> adapterIN = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapterIN.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        adapterIN.add("Select input connection");
        for (MidiDeviceInfo device : midiDevices) {
            String deviceName = device.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
            adapterIN.add(deviceName);
        }

        spinOutput.setAdapter(adapterIN);
    }

    private void stopMidi() {
        if (midiOutputPort != null) {
            try {
                midiOutputPort.close();
                midiOutputPort = null;
            } catch (IOException e) { }
        }
        textView.setText("Stopped");
    }

    private void startMidi() {
        stopMidi();

        if (midiManager == null) {
            textView.setText("Cannot find the MIDI Manager");
            return;
        }

        if (instrument == 0 || selectedOutputDevice == null) {
            textView.setText("Please set the options first");
            return;
        }

        navMode = true;

        midiManager.openDevice(selectedOutputDevice, new MidiManager.OnDeviceOpenedListener() {
            @Override
            public void onDeviceOpened(MidiDevice midiDevice) {
                if (midiDevice == null) {
                    return;
                }

                midiOutputPort = midiDevice.openOutputPort(0);
                if (midiOutputPort != null) {
                    MidiReceiver receiver = new MidiReceiver() {
                        @Override
                        public void onSend(byte[] data, int offset, int count, long timestamp) {
                            try {
                                if (input == null) {
                                    textView.setText("MIDI Hero Keyboard absent");
                                    return;
                                }

                                if (data != null) {
                                    boolean res = false;
                                    int pos = 0;
                                    while (pos <= (count + 3) && data.length >= (offset + count)) {
                                        byte cmd = data[offset + pos];
                                        byte dat_a = data[offset + pos + 1];
                                        byte dat_b = data[offset + pos + 2];
                                        pos += 3;
                                        switch (instrument) {
                                            case 1:
                                                if ((cmd & 0xF0) == 0x90) {
                                                    char evt = 0;
                                                    switch (dat_a) {
                                                        case 0x2E:      // Hi-Hat Open (NEW)
                                                            navMode = !navMode;
                                                            textView.setText("Navigation Mode " + (navMode ? "On" : "Off"));
                                                            break;
                                                        case 0x29:      // Floor tom
                                                            evt = 97;      // Green (a)
                                                            break;
                                                        case 0x26:      // Snare
                                                            evt = 115;      // Red (s)
                                                            break;
                                                        case 0x2A:      // Hi Hat Closed
                                                            evt = 106;      // Yellow (j)
                                                            break;
                                                        case 0x30:      // Hi Tom
                                                            evt = 107;      // Blue (k)
                                                            break;
                                                        case 0x24:      // Bass Drum
                                                            evt = 108;      // Orange (l)
                                                            break;
                                                        case 0x31:      // Cymbal (NEW)
                                                            evt = 105;      // Yellow Cymbal (i)
                                                            if (navMode) {
                                                                evt = 122;   // Strum Up (z)
                                                            }
                                                            break;
                                                        case 0x2D:      // Mid Tom (NEW)
                                                            evt = 111;      // Blue Cymbal (o)
                                                            if (navMode) {
                                                                evt = 104;  // Select & Star Power (h)
                                                            }
                                                            break;
                                                        case 0x33:      // Ride (NEW)
                                                            evt = 112;      // Green Cymbal - (p)
                                                            if (navMode) {
                                                                evt = 120;   // Strum Down (x)
                                                            }
                                                            break;
                                                        case 0x2C:      // Hi-Hat Pedal (NEW)
                                                            evt = 110;      // Open (n)
                                                            if (navMode) {
                                                                evt = 109;  // Start (m)
                                                            }
                                                            break;
                                                    }

                                                    if (evt != 0) {
                                                        res = input.doPress(evt);
                                                        if (res) {
                                                            textView.setText("Sent key " + evt);
                                                        }
                                                    }
                                                    break;
                                                }
                                                break;
                                            case 2:
                                                if ((cmd & 0xF0) == 0x90) {
                                                    char evt = 0;
                                                    switch (dat_a) {
                                                        case 0x28:      // Str 1 Fret 0
                                                            evt = 122;      // Strum Up (z)
                                                            break;
                                                        case 0x2A:      // Str 1 Fret 2
                                                            evt = 120;      // Strum Down (x)
                                                            break;
                                                        case 0x2C:      // Str 1 Fret 4
                                                            evt = 98;      // Whammy (b)
                                                            break;
                                                        case 0x2E:      // Str 1 Fret 6
                                                            evt = 110;      // Open (n)
                                                            break;
                                                    }

                                                    if (evt != 0) {
                                                        res = input.doPress(evt);
                                                        if (res) {
                                                            textView.setText("Sent key " + evt);
                                                        }
                                                    }
                                                } else if ((cmd & 0xF0) == 0xB0) {
                                                    int evt_a = -1;
                                                    int evt_b = 0;
                                                    if (dat_a == 0x13 && dat_b == 0x7F) {
                                                        char evt = 104;     // Select & Star Power (h)
                                                        res = input.doPress(evt);
                                                        if (res) {
                                                            textView.setText("Sent key " + evt);
                                                        }
                                                    } else if (dat_a == 0x14 && dat_b == 0x7F) {
                                                        char evt = 109;     // Start (m)
                                                        res = input.doPress(evt);
                                                        if (res) {
                                                            textView.setText("Sent key " + evt);
                                                        }
                                                    } else {
                                                        switch (dat_b) {
                                                            case (0x2D):        // Green Up
                                                                evt_a = KeyEvent.ACTION_UP;
                                                                evt_b = KeyEvent.KEYCODE_A;
                                                                break;
                                                            case (0x2F):        // Green Down (a)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_A;
                                                                break;
                                                            case (0x32):        // Red Up
                                                                evt_a = KeyEvent.ACTION_UP;
                                                                evt_b = KeyEvent.KEYCODE_S;
                                                                break;
                                                            case (0x35):        // Red Down (s)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_S;
                                                                break;
                                                            case (0x37):        // Yellow Up
                                                                evt_a = KeyEvent.ACTION_UP;
                                                                evt_b = KeyEvent.KEYCODE_J;
                                                                break;
                                                            case (0x3B):        // Yellow Down (j)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_J;
                                                                if (dat_a == 0x6A) {  // Blue Up
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                    evt_b = KeyEvent.KEYCODE_K;
                                                                }
                                                                break;
                                                            case (0x40):        // Blue Down (k)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_K;
                                                                if (dat_a == 0x69) {  // Orange Up
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                    evt_b = KeyEvent.KEYCODE_L;
                                                                }
                                                                break;
                                                            case (0x46):        // Orange Down (l)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_L;
                                                                break;
                                                        }
                                                    }

                                                    if (evt_a >= 0) {
                                                        res = input.doEvent(new KeyEvent(evt_a, evt_b));
                                                        if (res) {
                                                            textView.setText("Sent event " + evt_a + " " + evt_b);
                                                        }
                                                    }
                                                }
                                                break;
                                            case 3:
                                                int evt_a = -1;
                                                if ((cmd & 0xF0) == 0x80) {
                                                    evt_a = KeyEvent.ACTION_UP;
                                                } else if ((cmd & 0xF0) == 0x90) {
                                                    evt_a = KeyEvent.ACTION_DOWN;
                                                }
                                                if (evt_a >= 0) {
                                                    int evt_b = 0;
                                                    switch (dat_a) {
                                                        case 0x3D:      // Up (x)
                                                        case 0x31:
                                                            evt_b = KeyEvent.KEYCODE_Z;
                                                            break;
                                                        case 0x3F:      // Down (z)
                                                        case 0x33:
                                                            evt_b = KeyEvent.KEYCODE_X;
                                                            break;
                                                        case 0x3C:      // Green (a)
                                                        case 0x30:
                                                            evt_b = KeyEvent.KEYCODE_A;
                                                            break;
                                                        case 0x3E:      // Red (s)
                                                        case 0x32:
                                                            evt_b = KeyEvent.KEYCODE_S;
                                                            break;
                                                        case 0x40:      // Yellow (j)
                                                        case 0x34:
                                                            evt_b = KeyEvent.KEYCODE_J;
                                                            break;
                                                        case 0x41:      // Blue (k)
                                                        case 0x35:
                                                            evt_b = KeyEvent.KEYCODE_K;
                                                            break;
                                                        case 0x43:      // Orange (l)
                                                        case 0x37:
                                                            evt_b = KeyEvent.KEYCODE_L;
                                                            break;
                                                        case 0x42:      // Select (h)
                                                        case 0x36:
                                                            evt_b = KeyEvent.KEYCODE_H;
                                                            break;
                                                        case 0x44:      // Start (m)
                                                        case 0x38:
                                                            evt_b = KeyEvent.KEYCODE_M;
                                                            break;
                                                        case 0x45:      // Whammy (b)
                                                        case 0x39:
                                                            evt_b = KeyEvent.KEYCODE_B;
                                                            break;
                                                        case 0x46:      // Open (n)
                                                        case 0x3A:
                                                            evt_b = KeyEvent.KEYCODE_N;
                                                            break;
                                                    }

                                                    if (evt_b != 0) {
                                                        res = input.doEvent(new KeyEvent(evt_a, evt_b));
                                                        if (res) {
                                                            textView.setText("Sent event " + evt_a + " " + evt_b);
                                                        }
                                                    }
                                                }
                                                break;
                                        }
                                    }

                                    /*
                                    if (! res) {
                                        textView.setText("Nothing sent");
                                    }
                                    */
                                }
                            } catch (Exception e) {
                                textView.setText(e.toString());
                            }
                        }
                    };
                    midiOutputPort.connect(receiver);

                    textView.setText("Started");
                }
            }
        }, null);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (midiOutputPort != null) {
            try {
                midiOutputPort.close();
                midiOutputPort = null;
            } catch (IOException e) { }
        }
    }
}