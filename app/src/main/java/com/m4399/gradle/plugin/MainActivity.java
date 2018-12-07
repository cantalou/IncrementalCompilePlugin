package com.m4399.gradle.plugin;

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
        TextView value0 = findViewById(R.id.value0);
        value0.setText("app name :" + getString(R.string.app_name6));

        TextView value01 = findViewById(R.id.value01);
        value01.setText("app name :" + getString(R.string.app_name9));

        TextView value1 = findViewById(R.id.value1);
        value1.setText("Value from MainActivity :" + 123);

        NewClass newClass = new NewClass();

        TextView value2 = findViewById(R.id.value2);
        value2.setText("Value from Utils :" + Utils.get() + ", " + Utils.get1() + ", " + Utils.constant2 + ", " + NewClass.newMethod() + ", " + newClass.newMethod1());
    }


    interface Bean{

    }

}
