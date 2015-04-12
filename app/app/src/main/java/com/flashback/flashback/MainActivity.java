package com.flashback.flashback;

import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;

import com.astuetz.PagerSlidingTabStrip;
import com.google.android.gms.common.ConnectionResult;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewPager);
        viewPager.setAdapter(new MainPagerAdapter(getSupportFragmentManager(), this));

        PagerSlidingTabStrip tabStrip = (PagerSlidingTabStrip) findViewById(R.id.tabStrip);
        tabStrip.setViewPager(viewPager);
        tabStrip.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/fontawesome-webfont.ttf"), Typeface.NORMAL);

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        tabStrip.setTabPaddingLeftRight((size.x / 6) - 21);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class MainPagerAdapter extends FragmentPagerAdapter {
        private static int NUM_PAGES = 3;
        private MainActivity activity;

        public MainPagerAdapter(FragmentManager fm, MainActivity activity) {
            super(fm);
            this.activity = activity;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new CaptureFragment();
                case 1:
                    return new CapsulesFragment();
                case 2:
                    return new FriendsFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            String title = null;

            Typeface font = Typeface.createFromAsset(activity.getAssets(), "fonts/fontawesome-webfont.ttf");
            SpannableStringBuilder styled;
            switch (position) {
                case 0:
                    title = "\uf030";

                    styled = new SpannableStringBuilder(title);
                    styled.setSpan(new CustomTypefaceSpan(font), 0, title.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

                    return styled;
                case 1:
                    title = "\uf1c5";

                    styled = new SpannableStringBuilder(title);
                    styled.setSpan(new CustomTypefaceSpan(font), 0, title.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

                    return styled;
                case 2:
                    title = "\uf0c0";

                    styled = new SpannableStringBuilder(title);
                    styled.setSpan(new CustomTypefaceSpan(font), 0, title.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

                    return styled;
                default:
                    return null;
            }
        }
    }
}
