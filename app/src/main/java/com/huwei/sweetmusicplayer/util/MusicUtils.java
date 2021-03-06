package com.huwei.sweetmusicplayer.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import com.huwei.sweetmusicplayer.SweetApplication;
import com.huwei.sweetmusicplayer.contains.IContain;
import com.huwei.sweetmusicplayer.dao.AlbumInfoDao;
import com.huwei.sweetmusicplayer.dao.DaoSession;
import com.huwei.sweetmusicplayer.dao.MusicInfoDao;
import com.huwei.sweetmusicplayer.models.AlbumInfo;
import com.huwei.sweetmusicplayer.models.MusicInfo;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 音乐查询工具类
 * @author jayce
 * @date 2015/06/11
 */
public class MusicUtils implements IContain{
    public static final Uri sArtworkUri = Uri
            .parse("content://media/external/audio/albumart");
    private static final BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
    private static final BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
    private static final HashMap<Long, Bitmap> sArtCache = new HashMap<Long, Bitmap>();

    private static String[] proj_music = new String[] {
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION };

    private static String[] proj_album = new String[] { MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS, MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART,MediaStore.Audio.Albums.ARTIST };

    private static String[] proj_artist = new String[] {
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS };

    /**
     *
     * 查询音乐信息
     */
    public static List<MusicInfo> queryMusic(Context context){
        DaoSession session=SweetApplication.getDaoSession();
        MusicInfoDao musicInfoDao=session.getMusicInfoDao();

        //TODO 内置存储卡也需要扫描
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        ContentResolver cr = context.getContentResolver();

        StringBuffer select = new StringBuffer(" 1=1 ");
        // 查询语句：检索出.mp3为后缀名，时长大于1分钟，文件大小大于1MB的媒体文件
        if(Environment.isFilterSize(context)) {
            select.append(" and " + MediaStore.Audio.Media.SIZE + " > " + FILTER_SIZE);
        }
        if(Environment.isFilterDuration(context)) {
            select.append(" and " + MediaStore.Audio.Media.DURATION + " > " + FILTER_DURATION);
        }

        if(musicInfoDao.count()!=0){
            return musicInfoDao.loadAll();
        }else{
            List<MusicInfo> musicInfoList=getMusicList(cr.query(uri, proj_music,
                    select.toString(), null,
                    MediaStore.Audio.Media.TITLE_KEY));

            for(MusicInfo musicInfo:musicInfoList){
                //以前的数据库中不存在这首歌曲
                if(musicInfoDao.load(musicInfo.getSongId())==null){
                    musicInfoDao.insert(musicInfo);
                }
            }

            return musicInfoDao.loadAll();
        }
    }

    public static List<MusicInfo> queryMusicByAlbumId(Long album_id){
        DaoSession session=SweetApplication.getDaoSession();
        MusicInfoDao musicInfoDao=session.getMusicInfoDao();

        return musicInfoDao.queryBuilder().where(MusicInfoDao.Properties.AlbumId.eq(album_id)).list();
    }

    /**
     * 查询专辑信息
     * @param context
     * @return
     */
    public static List<AlbumInfo> queryAlbum(Context context){
        DaoSession session=SweetApplication.getDaoSession();
        AlbumInfoDao albumInfoDao=session.getAlbumInfoDao();

        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        ContentResolver cr = context.getContentResolver();
        StringBuilder where = new StringBuilder(MediaStore.Audio.Albums._ID
                + " in (select distinct " + MediaStore.Audio.Media.ALBUM_ID
                + " from audio_meta where (1=1 ");

        if(Environment.isFilterSize(context)) {
            where.append(" and " + MediaStore.Audio.Media.SIZE + " > " + FILTER_SIZE);
        }
        if(Environment.isFilterDuration(context)) {
            where.append(" and " + MediaStore.Audio.Media.DURATION + " > " + FILTER_DURATION);
        }
        where.append("))");

        if(albumInfoDao.count()!=0){
            return albumInfoDao.loadAll();
        }else{
            //TODO 内置存储卡也需要扫描
            List<AlbumInfo> albumInfoList=getAlbumList(cr.query(uri,proj_album,
                    where.toString(),null, MediaStore.Audio.Media.ALBUM_KEY));
            for(AlbumInfo albumInfo:albumInfoList){
                albumInfoDao.insert(albumInfo);
            }

            return  albumInfoList;
        }
    }

    public static Bitmap getCachedArtwork(Context context,long album_id,int defaultResId){
        BitmapDrawable bd= (BitmapDrawable) context.getResources().getDrawable(defaultResId);
        return getCachedArtwork(context,album_id,bd.getBitmap());
    }
    /**
     * 获取艺术家图片
     * @param context
     * @param album_id
     * @param defaultBitmap
     * @return
     */
    public static Bitmap getCachedArtwork(Context context,long album_id,
                                          Bitmap defaultBitmap){
        Bitmap bitmap=null;
        synchronized(sArtCache){
            bitmap = sArtCache.get(album_id);
        }
        if(context==null){
            return defaultBitmap;
        }
        if(bitmap==null){
            int w = defaultBitmap.getWidth();
            int h = defaultBitmap.getHeight();
            bitmap=getArtworkQuick(context,album_id,w,h);
                synchronized (sArtCache){
                        sArtCache.put(album_id, bitmap);
                }
        }
        return bitmap == null?defaultBitmap:bitmap;
    }

    public static Bitmap getArtworkQuick(Context context,long album_id,int w,int h){
        w-=1;
        ContentResolver res=context.getContentResolver();
        Uri uri= ContentUris.withAppendedId(sArtworkUri,album_id);
        if(uri!=null){
            ParcelFileDescriptor fd=null;
            try {
                fd=res.openFileDescriptor(uri,"r");
                int sampleSize = 1;

                sBitmapOptionsCache.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),null,sBitmapOptionsCache);
                int nextWidth = sBitmapOptionsCache.outWidth>>1;
                int nextHeight=sBitmapOptionsCache.outHeight>>1;
                while (nextWidth > w &&nextHeight>h){
                    sampleSize<<= 1;
                    nextWidth >>= 1;
                    nextHeight>>= 1;
                }

                sBitmapOptionsCache.inSampleSize = sampleSize;
                sBitmapOptionsCache.inJustDecodeBounds = false;
                Bitmap b=BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),null,sBitmapOptionsCache);

                if(b!=null){
                    //按照所给的宽高缩放
                    if(sBitmapOptionsCache.outWidth!=w||
                            sBitmapOptionsCache.outHeight!=h){
                        Bitmap tmp = Bitmap.createScaledBitmap(b,w,h,true);

                        if(tmp!=b){
                            b.recycle();
                        }
                        b = tmp;
                    }
                }

                return b;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }finally {
                if(fd!=null){
                    try {
                        fd.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        return null;
    }

    public static String getAlbumArtPath(Context context,Long albumid) {
        String strAlbums = "content://media/external/audio/albums";
        String[] projection = new String[] {android.provider.MediaStore.Audio.AlbumColumns.ALBUM_ART };
        Cursor cur = context.getContentResolver().query(
                Uri.parse(strAlbums + "/" + Long.toString(albumid)),
                projection, null, null, null);
        String strPath = null;
        if (cur.getCount() > 0 && cur.getColumnCount() > 0) {
            cur.moveToNext();
            strPath = cur.getString(0);
        }
        cur.close();
        cur = null;
        return strPath;
    }

    public static ArrayList<MusicInfo> getMusicList(Cursor cursor){
        if(cursor==null){
            return null;
        }

        ArrayList<MusicInfo> musicInfoList= new ArrayList<>();
        while (cursor.moveToNext()){
            MusicInfo musicInfo=new MusicInfo();
            musicInfo.setSongId(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID)));
            musicInfo.setTitle(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)));
            musicInfo.setAlbumId(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)));
            musicInfo.setArtist(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)));
            musicInfo.setDuration(cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)));
            musicInfo.setPath(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)));
            musicInfo.setFavorite(false);
            musicInfoList.add(musicInfo);
        }
        return musicInfoList;
    }

    public static List<AlbumInfo> getAlbumList(Cursor cursor){
        if(cursor==null){
            return null;
        }

        List<AlbumInfo> albumInfoList=new ArrayList<>();
        while(cursor.moveToNext()){
            AlbumInfo albumInfo=new AlbumInfo();
            albumInfo.setAlbumId(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Albums._ID)));
            albumInfo.setAlbumArt(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)));
            albumInfo.setArtist(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST)));
            albumInfo.setNumSongs(cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS)));
            albumInfo.setTitle(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM)));
            albumInfoList.add(albumInfo);
        }
        return albumInfoList;
    }

//    public static List getArtist(Cursor cursor){
//        MediaStore.Audio.Artists.ARTIST;
//    }
}
