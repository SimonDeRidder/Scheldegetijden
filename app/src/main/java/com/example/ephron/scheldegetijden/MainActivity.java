package com.example.ephron.scheldegetijden;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
    private Map<SimpleDate, List<Pair<String,List<List<Pair<Integer,Integer>>>>>> cache;
    private Calendar currentCalendar = Calendar.getInstance();
    private DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener()
    {
        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth)
        {
            currentCalendar.set(Calendar.YEAR, year);
            currentCalendar.set(Calendar.MONTH, monthOfYear);
            currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            onDateSelect();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText edittext= (EditText) findViewById(R.id.dateDisplay);
        edittext.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                new DatePickerDialog(MainActivity.this, dateSetListener,
                        currentCalendar.get(Calendar.YEAR),
                        currentCalendar.get(Calendar.MONTH),
                        currentCalendar.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        readCache();
        flushOldCache();
        currentCalendar = Calendar.getInstance();
        onDateSelect();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        readCache();
        flushOldCache();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        saveCache();
    }

    @SuppressWarnings("unchecked")
    public void readCache()
    {
        File cacheDir = getCacheDir();
        File cacheFile = new File(cacheDir,"cache.ser");
        try
        {
            ObjectInputStream is = new ObjectInputStream(new FileInputStream(cacheFile));
            cache = (Map<SimpleDate, List<Pair<String,List<List<Pair<Integer,Integer>>>>>>) is.readObject();
            is.close();
        } catch (IOException | ClassNotFoundException e)
        {// file not found or unrecognised, initialise empty cache
            Log.e("SharedVariable", e.getMessage(), e);
            cache = new HashMap<>(5);
        }
    }

    public void saveCache()
    {
        if (!cache.isEmpty())
        {
            File cacheDir = getCacheDir();
            File cacheFile = new File(cacheDir, "cache.ser");
            ObjectOutputStream os = null;
            try
            {
                os = new ObjectOutputStream(new FileOutputStream(cacheFile));
                os.writeObject(cache);
            } catch (IOException e)
            {// file not writable or memory full, just do nothing
                Log.e("SharedVariable", e.getMessage(), e);
            } finally
            {
                if (os!=null)
                {
                    try
                    {
                        os.close();
                    } catch (IOException e)
                    {
                        Log.e("SharedVariable", e.getMessage(), e);
                    }
                }
            }
        }
    }

    public void flushOldCache()
    {
        Calendar today = Calendar.getInstance();
        Iterator<Map.Entry<SimpleDate, List<Pair<String,List<List<Pair<Integer,Integer>>>>>>> it = cache.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<SimpleDate, List<Pair<String,List<List<Pair<Integer,Integer>>>>>> pair = it.next();
            if (pair.getKey().compare(today)<-1)
            {// delete entries more than a day old
                it.remove();
            }
        }
    }

    public void onDateSelect()
    {
        DateFormat dateFormat = android.text.format.DateFormat.getLongDateFormat(getApplicationContext());
        ((EditText) findViewById(R.id.dateDisplay)).setText(dateFormat.format(currentCalendar.getTime()));
        SimpleDate displayDate = new SimpleDate(currentCalendar.get(Calendar.YEAR),
                                                currentCalendar.get(Calendar.MONTH),
                                                currentCalendar.get(Calendar.DAY_OF_MONTH));
        List<Pair<String,List<List<Pair<Integer,Integer>>>>> table = cache.get(displayDate);
        if (table==null)
        {
            new DataRetriever().execute("http://www.waterinfo.be/default.aspx?path=NL/HIC/GetijverwachtingenPopup&");
        } else
        {
            displayTable(table);
        }
    }

    private void tryUpdate(String html)
    {
        if (html.compareTo("")==0)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.no_connection);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int id) {}//do nothing
            });
            builder.create().show();
        }
        else
        {
            parseHtml(html);
            SimpleDate displayDate = new SimpleDate(currentCalendar.get(Calendar.YEAR),
                                                    currentCalendar.get(Calendar.MONTH),
                                                    currentCalendar.get(Calendar.DAY_OF_MONTH));
            List<Pair<String, List<List<Pair<Integer, Integer>>>>> table = cache.get(displayDate);
            if (table == null)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.no_data_for_date);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id) {}//do nothing
                });
                builder.create().show();
                // try reverting to today
                Calendar temp = Calendar.getInstance();
                SimpleDate temp2 = new SimpleDate(temp.get(Calendar.YEAR), temp.get(Calendar.MONTH), temp.get(Calendar.DAY_OF_MONTH));
                if (temp2.compare(currentCalendar) != 0)
                {
                    currentCalendar = temp;
                    onDateSelect();
                }
            } else
            {
                displayTable(table);
            }
        }
    }

    private void parseHtml(String html)
    {
        boolean sourceError = false;
        int startInd = html.indexOf("<h2>");
        int endInd, tempInd, dataPoint1, dataPoint2, dataPoint3;
        String tempStr;
        Map<SimpleDate, List<Pair<String, List<List<Pair<Integer, Integer>>>>>> tempCache = new HashMap<>(3);
        SimpleDate baseDate;
        List<String> tempNames = new ArrayList<>(7);
        List<SimpleDate> tempDates = new ArrayList<>(7);
        List<Pair<Integer, Integer>> tempTimes = new ArrayList<>(7);
        if (startInd == -1)
        {
            sourceError = true;
        } else
        {
            endInd = html.indexOf("</div><script", startInd);
            if (endInd == -1)
            {
                sourceError = true;
            } else
            {
                while (startInd!=-1)
                {
                    html = html.substring(startInd+4, endInd+13).trim();
                    int highInd;
                    if (html.startsWith("Hoogwater"))
                    {
                        highInd = 0;
                    } else if (html.startsWith("Laagwater"))
                    {
                        highInd = 1;
                    } else
                    {
                        sourceError = true;
                        break;
                    }
                    html = (html.substring(9)).trim();
                    startInd = html.indexOf("/");
                    if (startInd == -1)
                    {
                        sourceError = true;
                        break;
                    }
                    try
                    {
                        dataPoint1 = Integer.parseInt(html.substring(startInd - 2, startInd));
                        dataPoint2 = Integer.parseInt(html.substring(startInd + 1, startInd + 3));
                        dataPoint3 = Integer.parseInt(html.substring(startInd + 4, startInd + 8));
                    } catch (NumberFormatException e)
                    {
                        sourceError = true;
                        break;
                    }
                    baseDate = new SimpleDate(dataPoint3, dataPoint2 - 1, dataPoint1);
                    html = html.substring(startInd + 8).trim();
                    // read placenames
                    tempNames.clear();
                    startInd = html.indexOf("<th style=\"border: 1px solid #dddddd\" width=\"14%\" align=\"center\">");
                    tempInd = html.indexOf("</tr>", startInd + 65);
                    if (startInd == -1 || tempInd == -1)
                    {
                        sourceError = true;
                        break;
                    }
                    while ((startInd < tempInd) && startInd!=-1)
                    {
                        endInd = html.indexOf("</th>", startInd + 65);
                        if (endInd == -1)
                        {
                            sourceError = true;
                            break;
                        }
                        tempNames.add(html.substring(startInd + 65, endInd).trim());
                        html = html.substring(endInd + 5).trim();
                        startInd = html.indexOf("<th style=\"border: 1px solid #dddddd\" width=\"14%\" align=\"center\">");
                        tempInd = html.indexOf("</tr>");
                        if (tempInd==-1)
                        {
                            sourceError = true;
                            break;
                        }
                    }
                    if (sourceError)
                    {
                        break;
                    }
                    // read dates
                    tempDates.clear();
                    startInd = html.indexOf("<td style=\"border: 1px solid #dddddd\" align=\"right\">");
                    tempInd = html.indexOf("</tr>", startInd + 52);
                    if (startInd == -1 || tempInd == -1)
                    {
                        sourceError = true;
                        break;
                    }
                    while ((startInd < tempInd) && startInd!=-1)
                    {
                        endInd = html.indexOf("</td>", startInd + 52);
                        if (endInd == -1)
                        {
                            sourceError = true;
                            break;
                        }
                        tempStr = html.substring(startInd + 52, endInd).trim();
                        try
                        {
                            dataPoint1 = Integer.parseInt(tempStr.substring(0, 2));
                            dataPoint2 = Integer.parseInt(tempStr.substring(3, 5));
                        } catch (NumberFormatException e)
                        {
                            sourceError = true;
                            break;
                        }
                        if (baseDate.MONTH == 11 && baseDate.DAY == 31 && dataPoint1 == 1 && dataPoint2 == 1)
                        {// new year!
                            tempDates.add(new SimpleDate(baseDate.YEAR + 1, 0, 1));
                        } else
                        {
                            tempDates.add(new SimpleDate(baseDate.YEAR, dataPoint2 - 1, dataPoint1));
                        }
                        html = html.substring(endInd + 5).trim();
                        startInd = html.indexOf("<td style=\"border: 1px solid #dddddd\" align=\"right\">");
                        tempInd = html.indexOf("</tr>");
                        if (tempInd == -1)
                        {
                            sourceError = true;
                            break;
                        }
                    }
                    if (sourceError)
                    {
                        break;
                    }
                    // read times
                    tempTimes.clear();
                    startInd = html.indexOf("<td style=\"border: 1px solid #dddddd\" align=\"right\">");
                    tempInd = html.indexOf("</tr>", startInd + 52);
                    if (startInd == -1 || tempInd == -1)
                    {
                        sourceError = true;
                        break;
                    }
                    while ((startInd < tempInd) && startInd!=-1)
                    {
                        endInd = html.indexOf("</td>", startInd + 52);
                        if (endInd == -1)
                        {
                            sourceError = true;
                            break;
                        }
                        tempStr = html.substring(startInd + 52, endInd).trim();
                        try
                        {
                            dataPoint1 = Integer.parseInt(tempStr.substring(0, 2));
                            dataPoint2 = Integer.parseInt(tempStr.substring(3, 5));
                        } catch (NumberFormatException e)
                        {
                            sourceError = true;
                            break;
                        }
                        if (dataPoint1 > 23 || dataPoint2 > 59)
                        {
                            sourceError = true;
                            break;
                        }
                        tempTimes.add(new Pair<>(dataPoint1, dataPoint2));
                        html = html.substring(endInd + 5).trim();
                        startInd = html.indexOf("<td style=\"border: 1px solid #dddddd\" align=\"right\">");
                        tempInd = html.indexOf("</tr>");
                        if (tempInd == -1)
                        {
                            sourceError = true;
                            break;
                        }
                    }
                    if (sourceError)
                    {
                        break;
                    }
                    // use templists to fill tempCache
                    if (tempNames.size() != tempDates.size() || tempNames.size() != tempTimes.size())
                    {
                        sourceError = true;
                        break;
                    }
                    for (int a = 0; a < tempNames.size(); a++)
                    {
                        //Map<SimpleDate, List<Pair<String, List<Pair<Integer, Integer>>[]>>>
                        if (tempCache.containsKey(tempDates.get(a)))
                        {
                            //loop over list to see if name already exists
                            boolean found = false;
                            for (int b = 0; b < tempCache.get(tempDates.get(a)).size(); b++)
                            {
                                if (tempCache.get(tempDates.get(a)).get(b).first.compareTo(tempNames.get(a)) == 0)
                                {
                                    found = true;
                                    tempCache.get(tempDates.get(a)).get(b).second.get(highInd).add(tempTimes.get(a));
                                    break;
                                }
                            }
                            if (!found)
                            {
                                tempCache.get(tempDates.get(a)).add(new Pair<String, List<List<Pair<Integer, Integer>>>>(tempNames.get(a), new ArrayList<List<Pair<Integer, Integer>>>(2)));
                                tempCache.get(tempDates.get(a)).get(tempCache.get(tempDates.get(a)).size() - 1).second.add(new ArrayList<Pair<Integer, Integer>>());
                                tempCache.get(tempDates.get(a)).get(tempCache.get(tempDates.get(a)).size() - 1).second.add(new ArrayList<Pair<Integer, Integer>>());
                                tempCache.get(tempDates.get(a)).get(tempCache.get(tempDates.get(a)).size() - 1).second.get(highInd).add(tempTimes.get(a));
                            }
                        } else
                        {
                            tempCache.put(tempDates.get(a), new ArrayList<Pair<String, List<List<Pair<Integer, Integer>>>>>(7));
                            tempCache.get(tempDates.get(a)).add(new Pair<String, List<List<Pair<Integer, Integer>>>>(tempNames.get(a), new ArrayList<List<Pair<Integer, Integer>>>(2)));
                            tempCache.get(tempDates.get(a)).get(tempCache.get(tempDates.get(a)).size() - 1).second.add(new ArrayList<Pair<Integer, Integer>>());
                            tempCache.get(tempDates.get(a)).get(tempCache.get(tempDates.get(a)).size() - 1).second.add(new ArrayList<Pair<Integer, Integer>>());
                            tempCache.get(tempDates.get(a)).get(tempCache.get(tempDates.get(a)).size() - 1).second.get(highInd).add(tempTimes.get(a));
                        }
                    }
                    // done, find next table
                    startInd = html.indexOf("<h2>");
                    endInd = html.indexOf("</div><script", startInd);
                    if (endInd == -1)
                    {
                        sourceError = true;
                    }
                }
            }
        }
        if (sourceError)
        {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.sourceError);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int id) {}//do nothing
            });
            builder.create().show();
            return;
        }
        // merge with cache
        cache.putAll(tempCache);
    }

    private class DataRetriever extends AsyncTask<String, Void, String>
    {

        private Exception exception = null;

        protected String doInBackground(String... urls)
        {
            StringBuilder html = new StringBuilder();
            try
            {
                InputStream in = (new URI(urls[0]).toURL()).openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                for (String line; (line = reader.readLine()) != null; )
                {
                    html.append(line);
                }
                in.close();
            } catch (Exception e)
            {
                this.exception = e;
                return null;
            }
            return html.toString();
        }

        protected void onPostExecute(String html)
        {
            if (this.exception==null)
            {
                tryUpdate(html);
            } else
            {
                tryUpdate("");
            }
        }
    }

    private void displayTable(List<Pair<String,List<List<Pair<Integer,Integer>>>>> table)
    {
        GridLayout grid = (GridLayout) findViewById(R.id.gridDisplay);
        grid.removeAllViews();
        Pair<List<String>,List<Pair<Integer,List<String>>>> sortedTable = sortAndArrange(table);
        grid.setColumnCount(sortedTable.second.size()+1);
        grid.setRowCount(sortedTable.first.size()+2);
        grid.setForegroundGravity(Gravity.CENTER);
        // set names
        for (int a=0; a<sortedTable.first.size(); a++)
        {
            TextView name = new TextView(this);
            name.setText(sortedTable.first.get(a));
            if (a%2==1)
            {
                name.setBackgroundColor(ContextCompat.getColor(this, R.color.nameDarkColor));
            }
            else
            {
                name.setBackgroundColor(ContextCompat.getColor(this, R.color.nameLightColor));
            }
            name.setGravity(Gravity.CENTER);
            name.setTypeface(name.getTypeface(), 1);
            name.setTextColor(ContextCompat.getColor(this, R.color.black));
            grid.addView(name,new GridLayout.LayoutParams(GridLayout.spec(2+a, 1), GridLayout.spec(0, 1, (float)1.0)));
        }
        // set rest of table column by column
        for (int a=0; a<sortedTable.second.size(); a++)
        {
            // set header
            TextView header = new TextView(this);
            int lightColor, darkColor;
            if (sortedTable.second.get(a).first==0)
            {
                header.setText(R.string.high_header);
                lightColor = ContextCompat.getColor(this, R.color.highLightColor);
                darkColor = ContextCompat.getColor(this, R.color.highDarkColor);
            }
            else
            {
                header.setText(R.string.low_header);
                lightColor = ContextCompat.getColor(this, R.color.lowLightColor);
                darkColor = ContextCompat.getColor(this, R.color.lowDarkColor);
            }
            header.setBackgroundColor(lightColor);
            header.setGravity(Gravity.CENTER);
            header.setTypeface(header.getTypeface(), 1);
            header.setTextColor(ContextCompat.getColor(this, R.color.black));
            grid.addView(header,new GridLayout.LayoutParams(GridLayout.spec(0, 1), GridLayout.spec(1+a, 1, (float)1.0)));
            TextView sep = new TextView(this);
            sep.setText("");
            sep.setBackgroundColor(ContextCompat.getColor(this, R.color.black));
            sep.setHeight(5);
            grid.addView(sep,new GridLayout.LayoutParams(GridLayout.spec(1, 1), GridLayout.spec(1+a, 1, (float)1.0)));
            for (int b=0; b<sortedTable.first.size(); b++)
            {
                TextView cell = new TextView(this);
                cell.setText(sortedTable.second.get(a).second.get(b));
                if (b%2==1)
                {
                    cell.setBackgroundColor(darkColor);
                }
                else
                {
                    cell.setBackgroundColor(lightColor);
                }
                cell.setGravity(Gravity.CENTER);
                grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(2+b, 1), GridLayout.spec(1+a, 1, (float)1.0)));
            }
        }
    }

    private Pair<List<String>,List<Pair<Integer,List<String>>>> sortAndArrange
            (List<Pair<String,List<List<Pair<Integer,Integer>>>>> table)
    {
        List<String> names = new ArrayList<>(table.size());
        for (int a=0; a<table.size(); a++)
        {
            names.add(table.get(a).first);
        }
        List<Pair<Integer,List<String>>> sTable = new ArrayList<>();
        Pair<List<Integer>,List<List<Integer>>> currentMin;
        Pair<Integer,Integer> tempTime;
        int hiLo, count = 0;
        boolean allEmpty = false;
        boolean[] empty = new boolean[table.size()];
        for (int a=0; a<table.size(); a++)
        {
            empty[a] = false;
        }
        while (!allEmpty)
        {
            currentMin = findMinimumEntries(table);
            hiLo = currentMin.first.get(1);
            sTable.add(new Pair<Integer, List<String>>(hiLo, new ArrayList<String>(table.size())));
            for (int a=0; a<table.size(); a++)
            {
                if (currentMin.second.get(a).get(0)==hiLo)
                {
                    tempTime = table.get(a).second.get(hiLo).get(currentMin.second.get(a).get(1));
                    sTable.get(count).second.add(a,String.format(Locale.US,"%02d",tempTime.first).concat(":").concat(String.format(Locale.US,"%02d",tempTime.second)));
                    table.get(a).second.get(hiLo).remove((int)currentMin.second.get(a).get(1));
                    if((table.get(a).second.get(hiLo).size()==0)&&(table.get(a).second.get((hiLo+1)%2).size()==0))
                    {
                        table.get(a).second.clear();
                        empty[a] = true;
                    }
                }
                else
                {
                    sTable.get(count).second.add(a,"");
                }
            }
            allEmpty = true;
            for (int a=0; a<table.size()-1; a++)
            {
                allEmpty = allEmpty && empty[a];
            }
            count++;
        }
        return new Pair<>(names,sTable);
    }

    private Pair<List<Integer>,List<List<Integer>>> findMinimumEntries(List<Pair<String,List<List<Pair<Integer,Integer>>>>> table)
    {
        List<Integer> totalMin = new ArrayList<>(3);
        totalMin.add(0,-1);
        totalMin.add(1,-1);
        totalMin.add(2,-1);
        List<List<Integer>> nameMin = new ArrayList<>(table.size());
        Pair<Integer,Integer> totalMinVal = new Pair<>(24,0);
        Pair<Integer,Integer> currentMinVal;
        for (int a=0; a<table.size(); a++)
        {
            nameMin.add(new ArrayList<Integer>(2));
            nameMin.get(a).add(0,-1);
            nameMin.get(a).add(1,-1);
            currentMinVal = new Pair<>(24,0);
            for (int b=0; b<table.get(a).second.size(); b++)
            {
                for (int c=0; c<table.get(a).second.get(b).size(); c++)
                {
                    if (compareTimes(table.get(a).second.get(b).get(c),totalMinVal)<0)
                    {
                        totalMinVal = table.get(a).second.get(b).get(c);
                        totalMin.set(0,a);
                        totalMin.set(1,b);
                        totalMin.set(2,c);
                    }
                    if (compareTimes(table.get(a).second.get(b).get(c),currentMinVal)<0)
                    {
                        currentMinVal = table.get(a).second.get(b).get(c);
                        nameMin.get(a).set(0,b);
                        nameMin.get(a).set(1,c);
                    }
                }
            }
        }
        return new Pair<>(totalMin,nameMin);
    }

    // first-second
    private int compareTimes(Pair<Integer,Integer> first, Pair<Integer,Integer> second)
    {
        return (first.first-second.first)*60 + first.second - second.second;
    }
}
