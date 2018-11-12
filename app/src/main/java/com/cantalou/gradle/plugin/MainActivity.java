package com.cantalou.gradle.plugin;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        testChange();
    }

    public void testChange() {

        TextView value0 = findViewById(R.id.value3);
        value0.setText("app name :" + getString(R.string.app_name1));

        TextView value1 = findViewById(R.id.value1);
        value1.setText("Value from MainActivity :" + 9);


        TextView value2 = findViewById(R.id.value2);
        value2.setText("Value from Utils :" + Utils.get() + Utils.constant2);
    }
}
