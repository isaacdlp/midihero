package com.isaacdlp.midihero;

import android.annotation.SuppressLint;

import android.os.Bundle;
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
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    public static MyInputMethodService input = null;

    private final String[] instruments = {
            "Drums",
            "Pro Drums",
            "Guitar 5-Fret",
            "Guitar 6-Fret",
            "Piano"
    };
    private int instrument = 0;
    private boolean navMode = false;

    private Spinner spinOutput;
    private TextView textView;

    private MidiManager midiManager;
    private MidiOutputPort midiOutputPort;
    private MidiDeviceInfo selectedOutputDevice;
    private final ArrayList<MidiDeviceInfo> midiDevices = new ArrayList<>();


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        midiManager = (MidiManager) getSystemService(MIDI_SERVICE);

        // Initialize UI elements
        spinOutput = findViewById(R.id.spinnerOutput);
        Spinner spinInstr = findViewById(R.id.spinnerInstr);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        textView = findViewById(R.id.textView);

        ArrayAdapter<String> adapterOUT = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapterOUT.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapterOUT.add("Select instrument");
        for (String s : instruments) adapterOUT.add(s);
        spinInstr.setAdapter(adapterOUT);

        // Set listeners for Spinner selections
        spinOutput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @SuppressLint("SetTextI18n")
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

            @SuppressLint("SetTextI18n")
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textView.setText("No input selected");
            }
        });

        spinInstr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                instrument = position;
                if (position > 0) {
                    textView.setText("Instrument selected: " + instruments[position - 1]);
                }
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textView.setText("No instrument selected");
            }
        });

        // Set listeners for Buttons
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
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

    @SuppressLint("SetTextI18n")
    private void refreshDeviceLists() {
        if (midiManager == null) {
            textView.setText("Cannot find the MIDI Manager");
            return;
        }

        stopMidi();

        selectedOutputDevice = null;

        midiDevices.clear();
        MidiDeviceInfo[] devices = midiManager.getDevices();
        midiDevices.addAll(Arrays.asList(devices));

        ArrayAdapter<String> adapterIN = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapterIN.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        adapterIN.add("Select input connection");
        for (MidiDeviceInfo device : midiDevices) {
            String deviceName = device.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
            adapterIN.add(deviceName);
        }

        spinOutput.setAdapter(adapterIN);
    }

    @SuppressLint("SetTextI18n")
    private void stopMidi() {
        if (midiOutputPort != null) {
            try {
                midiOutputPort.close();
                midiOutputPort = null;
            } catch (IOException ignored) { }
        }
        textView.setText("Stopped");
    }

    @SuppressLint("SetTextI18n")
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
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onSend(byte[] data, int offset, int count, long timestamp) {
                            try {
                                if (input == null) {
                                    textView.setText("MIDI Hero Keyboard absent");
                                    return;
                                }

                                if (data != null) {
                                    boolean res;
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
                                                            evt = 112;      // Green Cymbal (p)
                                                            if (navMode) {
                                                                evt = 120;   // Strum Down (x)
                                                            }
                                                            break;
                                                        case 0x2C:      // Hi-Hat Pedal (NEW)
                                                            evt = 110;      // 2x Kick (n)
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
                                                if ((cmd & 0xF0) == 0xB0) {
                                                    if (dat_a == 0x04) {
                                                        cmd = data[offset + 3];
                                                        dat_a = data[offset + 4];
                                                    }
                                                }

                                                if ((cmd & 0xF0) == 0x90) {
                                                    char evt = 0;
                                                    switch (dat_a) {
                                                        case 0x2E:      // Hi-Hat Open (NEW)
                                                            evt = 105;      // Yellow Cymbal (i)
                                                            break;
                                                        case 0x2B:      // Floor tom
                                                            evt = 97;      // Green (a)
                                                            break;
                                                        case 0x26:      // Snare
                                                            evt = 115;      // Red (s)
                                                            break;
                                                        case 0x30:      // Hi Tom
                                                            evt = 106;      // Yellow (j)
                                                            break;
                                                        case 0x24:      // Bass Drum
                                                            evt = 108;      // Orange (l)
                                                            break;
                                                        case 0x31:      // Cymbal (NEW)
                                                            evt = 111;      // Blue Cymbal (o)
                                                            break;
                                                        case 0x2D:      // Mid Tom (NEW)
                                                            evt = 107;      // Blue (k)
                                                            break;
                                                        case 0x33:      // Ride (NEW)
                                                            evt = 112;      // Green Cymbal (p)
                                                            break;
                                                        case 0x2C:      // Hi-Hat Pedal (NEW)
                                                            evt = 110;      // 2x Kick (n)
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
                                                return;     // Important! Alesis Nitro hack
                                            case 3:
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
                                                            evt = 110;      // 2x Kick (n)
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
                                            case 4:
                                                if ((cmd & 0xF0) == 0x90) {
                                                    char evt = 0;
                                                    switch (dat_a) {
                                                        case 0x28:
                                                        case 0x29:
                                                        case 0x2A:
                                                        case 0x2B:
                                                        case 0x2C:
                                                        case 0x2E:
                                                        case 0x2F:
                                                        case 0x30:
                                                            evt = 122;      // Strum up (z)
                                                            break;
                                                        case 0x40:
                                                        case 0x41:
                                                        case 0x42:
                                                        case 0x43:
                                                        case 0x44:
                                                        case 0x45:
                                                        case 0x46:
                                                        case 0x47:
                                                            evt = 120;      // Strum Down (x)
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
                                                        switch (dat_a) {
                                                            case (0x6E):     // Black 1 (a)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_A;
                                                                if (dat_b == 0x28) {
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                }
                                                                break;
                                                            case (0x6D):    // Black 2 (s)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_S;
                                                                if (dat_b == 0x2D) {
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                }
                                                                break;
                                                            case (0x6C):    // Black 3 (j)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_J;
                                                                if (dat_b == 0x32) {
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                }
                                                                break;
                                                            case (0x6B):    // White 1 (k)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_K;
                                                                if (dat_b == 0x37) {
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                }
                                                                break;
                                                            case (0x6A):    // White 2 (l)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_L;
                                                                if (dat_b == 0x3B) {
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                }
                                                                break;
                                                            case (0x69):    // White 3 (n)
                                                                evt_a = KeyEvent.ACTION_DOWN;
                                                                evt_b = KeyEvent.KEYCODE_N;
                                                                if (dat_b == 0x40) {
                                                                    evt_a = KeyEvent.ACTION_UP;
                                                                }
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
                                            case 5:
                                                int evt_a = -1;
                                                if ((cmd & 0xF0) == 0x80) {
                                                    evt_a = KeyEvent.ACTION_UP;
                                                } else if ((cmd & 0xF0) == 0x90) {
                                                    evt_a = KeyEvent.ACTION_DOWN;
                                                }
                                                if (evt_a >= 0) {
                                                    int evt_b = 0;
                                                    int mod = dat_a / 12;
                                                    switch (mod) {
                                                        case 1:      // Up (x)
                                                            evt_b = KeyEvent.KEYCODE_Z;
                                                            break;
                                                        case 3:      // Down (z)
                                                            evt_b = KeyEvent.KEYCODE_X;
                                                            break;
                                                        case 0:      // Green (a)
                                                            evt_b = KeyEvent.KEYCODE_A;
                                                            break;
                                                        case 2:      // Red (s)
                                                            evt_b = KeyEvent.KEYCODE_S;
                                                            break;
                                                        case 4:      // Yellow (j)
                                                            evt_b = KeyEvent.KEYCODE_J;
                                                            break;
                                                        case 5:      // Blue (k)
                                                            evt_b = KeyEvent.KEYCODE_K;
                                                            break;
                                                        case 7:      // Orange (l)
                                                            evt_b = KeyEvent.KEYCODE_L;
                                                            break;
                                                        case 6:      // Select (h)
                                                            evt_b = KeyEvent.KEYCODE_H;
                                                            break;
                                                        case 8:      // Start (m)
                                                            evt_b = KeyEvent.KEYCODE_M;
                                                            break;
                                                        case 9:      // White 3 (b)
                                                            evt_b = KeyEvent.KEYCODE_N;
                                                            break;
                                                        case 10:      // Whammy (b)
                                                            evt_b = KeyEvent.KEYCODE_B;
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
            } catch (IOException ignored) { }
        }
    }
}