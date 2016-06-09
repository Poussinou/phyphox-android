package de.rwth_aachen.phyphox;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Vector;

public class Protocol {
    protocolImplementation protocol;

    protected String buffer = "";

    Protocol(protocolImplementation protocol) {
        this.protocol = protocol;
    }

    public boolean canSend() {
        return protocol.canSend();
    }

    public boolean canReceive() {
        return protocol.canReceive();
    }

    public void send(OutputStream outStream, Vector<Vector<Double>> data) {
        protocol.processOutData(outStream, data);
    }

    public Vector<Vector<Double>> receive(InputStream inStream) {
        try {
            byte tempBuffer[] = new byte[256];
            int remaining = inStream.available();
            int readCount;
            while (remaining > 0) {
                readCount = inStream.read(tempBuffer, 0, 256);
                buffer += (new String(tempBuffer)).substring(0, readCount);
                remaining = inStream.available();
            }
        } catch (IOException e) {
        }

        return protocol.processInData(buffer);
    }

    public void clear() {
        buffer = "";
    }

    protected abstract static class protocolImplementation {

        protected abstract boolean canSend();
        protected abstract boolean canReceive();

        protected Vector<Vector<Double>> processInData(String buffer) {
            return null;
        }

        protected void processOutData(OutputStream outStram, Vector<Vector<Double>> data) {
        }

    }

    public static class simple extends protocolImplementation {
        String separator = "\n";

        simple() {

        }

        simple(String separator) {
            this.separator = separator;
        }

        @Override
        protected boolean canReceive() {
            return true;
        }

        @Override
        protected boolean canSend() {
            return true;
        }

        @Override
        protected Vector<Vector<Double>> processInData(String buffer) {
            Vector<Vector<Double>> result = new Vector<>();
            result.set(0, new Vector<Double>());
            int i = buffer.indexOf(separator);
            while (i >= 0) {
                double v = Double.NaN;
                try {
                    v = Double.valueOf(buffer.substring(0, i));
                    result.get(0).add(v);
                } catch (NumberFormatException e) {
                } finally {
                    buffer = buffer.substring(i);
                    i = buffer.indexOf(separator);
                }
            }
            return result;
        }

        @Override
        protected void processOutData(OutputStream outStram, Vector<Vector<Double>> data) {
            if (data.size() < 1)
                return;

            Iterator<Double> it = data.get(0).iterator();
            if (it.hasNext()) {
                try {
                    outStram.write((String.valueOf(it.next()) + separator).getBytes());
                } catch (Exception e) {

                }
            }
        }
    }

    public static class csv extends protocolImplementation {
        String separator = ",";

        csv() {

        }

        csv(String separator) {
            this.separator = separator;
        }

        @Override
        protected boolean canReceive() {
            return true;
        }

        @Override
        protected boolean canSend() {
            return true;
        }

        @Override
        protected Vector<Vector<Double>> processInData(String buffer) {
            Vector<Vector<Double>> result = new Vector<>();

            int nLine = 0;
            int i = buffer.indexOf("\n");
            while (i >= 0) {
                int column = 0;
                String line = buffer.substring(0, i);
                buffer = buffer.substring(i);
                int j = line.indexOf(separator);
                while (j >= 0 && line.length() > 0) {
                    double v = Double.NaN;
                    String valueStr;
                    if (j >= 0) {
                        valueStr = line.substring(0, j);
                        line = line.substring(j);
                        j = line.indexOf(separator);
                    } else {
                        valueStr = line;
                        line = "";
                    }
                    try {
                        v = Double.valueOf(valueStr);
                    } catch (NumberFormatException e) {
                    } finally {
                        if (column > result.size()) {
                            result.add(new Vector<Double>());
                            for (int k = 0; k < nLine-1; k++) {
                                result.get(column).add(Double.NaN);
                            }
                        }

                        result.get(column).add(v);

                        column++;
                    }
                }
                i = buffer.indexOf("\n");
                nLine++;
            }
            return result;
        }

        @Override
        protected void processOutData(OutputStream outStram, Vector<Vector<Double>> data) {
            Vector<Iterator<Double>> iterators = new Vector<>();
            for (Vector<Double> column : data) {
                iterators.add(column.iterator());
            }
            boolean dataLeft = true;
            while (dataLeft) {
                dataLeft = false;
                boolean first = true;
                String line = "";
                for (Iterator<Double> iterator : iterators) {
                    if (first) {
                        first = false;
                    } else {
                        line += separator;
                    }
                    if (iterator.hasNext()) {
                        dataLeft = true;
                        line += String.valueOf(iterator.next());
                    }
                }
                if (dataLeft) {
                    try {
                        outStram.write((line + "\n").getBytes());
                    } catch (Exception e) {

                    }
                }
            }
        }
    }

    public static class json extends protocolImplementation {
        Vector<String> names = null;

        json() {

        }

        json(Vector<String> names) {
            this.names = names;
        }

        @Override
        protected boolean canReceive() {
            return true;
        }

        @Override
        protected boolean canSend() {
            return true;
        }

        @Override
        protected Vector<Vector<Double>> processInData(String buffer) {
            Vector<Vector<Double>> result = new Vector<>();

            int i = buffer.indexOf("\n");
            while (i >= 0) {
                String line = buffer.substring(0, i);
                buffer = buffer.substring(i);

                try {
                    JSONObject json = new JSONObject(line);
                    for (int j = 0; i < names.size(); i++) {
                        result.add(new Vector<Double>());
                        if (json.has(names.get(j))) {
                            try {
                                JSONArray a = json.getJSONArray(names.get(j));
                                for (int k = 0; k < a.length(); k++) {
                                    double v = a.getDouble(k);
                                    result.get(i).add(v);
                                }
                            } catch (JSONException e1) {
                                try {
                                    double v = json.getDouble(names.get(j));
                                    result.get(i).add(v);
                                } catch (JSONException e2) {
                                    Log.e("bluetoothInput", "Could not parse " + names.get(j) + " as an array (" + e1.getMessage() + ") or a double (" + e2.getMessage() + ")");
                                }
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.e("bluetoothInput", "Could not parse JSON data: " + e.getMessage());
                }

                i = buffer.indexOf("\n");
            }
            return result;
        }

        @Override
        protected void processOutData(OutputStream outStream, Vector<Vector<Double>> data) {
            JSONObject json = new JSONObject();
            for (int i = 0; i < names.size(); i++) {
                JSONArray a = new JSONArray();
                Iterator it = data.get(i).iterator();
                while (it.hasNext())
                    a.put(it.next());
                try {
                    json.put(names.get(i), a);
                } catch (JSONException e) {
                    Log.e("bluetoothInput", "Could not construct JSON data: " + e.getMessage());
                }
            }
            try {
                outStream.write(json.toString().getBytes());
            } catch (Exception e) {

            }
        }
    }

}