package com.huwei.sweetmusicplayer.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.huwei.sweetmusicplayer.contains.IContain;

/**
 * @author Jayce
 * @date 2015/6/10
 */
public class Environment implements IContain{

    public static final String ENV="env";

    /**
     * 获取是否过滤文件大小
     * @param context
     * @return
     */
    public static boolean isFilterSize(Context context){
        SharedPreferences preferences=context.getSharedPreferences(ENV,Context.MODE_PRIVATE);
        return preferences.getBoolean(ENV_IS_FILTER_SIZE,true);
    }

    /**
     * 设置是否过滤文件大小
     * @param context
     * @param flag
     */
    public static void setIsFilterSize(Context context,boolean flag){
        SharedPreferences preferences=context.getSharedPreferences(ENV,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor=preferences.edit();
        editor.putBoolean(ENV_IS_FILTER_SIZE,flag);
        editor.commit();
    }

    /**
     * 获取是否过滤文件时长
     * @param context
     * @return
     */
    public static boolean isFilterDuration(Context context){
        SharedPreferences preferences=context.getSharedPreferences(ENV,Context.MODE_PRIVATE);
        return preferences.getBoolean(ENV_IS_FILTER_DURATION,true);
    }

    /**
     * 设置是否过滤文件时长
     * @param context
     * @param flag
     */
    public static void setIsFilterDuration(Context context,boolean flag){
        SharedPreferences preferences=context.getSharedPreferences(ENV,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor=preferences.edit();
        editor.putBoolean(ENV_IS_FILTER_DURATION,flag);
        editor.commit();
    }
}
