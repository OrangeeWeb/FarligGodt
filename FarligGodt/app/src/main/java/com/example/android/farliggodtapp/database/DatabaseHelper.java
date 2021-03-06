package com.example.android.farliggodtapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.android.farliggodtapp.api.Specie;

/**
 * Created by Agne Ødegaard on 06/10/16.
 */

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String databaseName = "FarligGodt.db";
    private static final String tableName = "saved";

    private static final String tableColValue = "value";
    private static final String tableColType = "type";


    public DatabaseHelper(Context context) {
        super(context, databaseName, null, 1);
    }

    /**
     * When the database is created
     * @param db
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + tableName + " (" + tableColValue + " VARCHAR(255), " + tableColType + " VARCHAR(255) PRIMARY KEY UNIQUE)");

        db.execSQL("CREATE TABLE `blacklist` (" +
                "  `id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  `scientificName` varchar(255) NOT NULL," +
                "  `navn` varchar(255) NOT NULL," +
                "  `risiko` varchar(255) NOT NULL," +
                "  `taxonID` int(11) NOT NULL," +
                "  `canEat` tinyint(1) NOT NULL DEFAULT '0'," +
                "  `family` varchar(255) NOT NULL," +
                "  `image` varchar(255) NOT NULL" +
                ")");
    }

    /**
     * When the app is uninstalled
     * @param db
     * @param oldVersion
     * @param newVersion
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + tableName);
        onCreate(db);
    }

    public boolean hazLatestBlacklist(){

        // app.version < farliggodt.agne.no/api/check -> version, do update
        return false;

    }

    public boolean updateBlacklist(Specie[] speices, String version){
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("delete from blacklist");

        for (Specie specie : speices) {
            ContentValues cv = new ContentValues();

            cv.put("taxonID"        , specie.getId());
            cv.put("scientificName" , specie.getLatin());
            cv.put("navn"           , specie.getName());
            cv.put("risiko"         , specie.getRisk());
            cv.put("canEat"         , specie.isEatable());
            cv.put("family"         , specie.getFamily());
            cv.put("image"          , specie.getImage());

            if(db.insert("blacklist", null, cv) < 0){
                return false;
            }
        }
        this.updateData("version", version);
        return true;
    }

    public String[] getBlacklistString (){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM blacklist", new String[] {});

        if(!c.moveToFirst()) {
            c.close();
            return null;
        }

        String[] blacklist = new String[c.getCount() - 1];

        while(c.moveToNext()){
            blacklist[c.getPosition() - 1] = c.getString(c.getColumnIndex("navn"));
        }

        c.close();

        return blacklist;
    }

    public String getTaxonByName(String name){
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor c = db.rawQuery("SELECT * FROM blacklist WHERE navn LIKE ?", new String[] {"%"+String.valueOf(name)+"%"});

        if(!c.moveToFirst()) {
            c.close();
            return null;
        }

        String taxonID = c.getString(c.getColumnIndex("taxonID"));
        c.close();

        return taxonID;
    }


    public Specie getSpecie(int id){
        Specie tax = new Specie();

        SQLiteDatabase db = this.getWritableDatabase();

        Cursor c = db.rawQuery("SELECT * FROM blacklist WHERE taxonID = ?", new String[] {String.valueOf(id) });

        if(!c.moveToFirst()) {
            c.close();
            return null;
        }

        tax.setName(c.getString(c.getColumnIndex("navn")));
        tax.setId(id);
        String img = c.getString(c.getColumnIndex("image"));
        Boolean eatable = false;
        if(img != "1"){
            eatable = true;
        }
        tax.setEatable(eatable);
        tax.setLatin(c.getString(c.getColumnIndex("scientificName")));
        tax.setRisk(c.getString(c.getColumnIndex("risiko")));
        tax.setFamily(c.getString(c.getColumnIndex("family")));
        tax.setImage(c.getString(c.getColumnIndex("image")));

        return tax;
    }

    /**
     * Insert a row into the database
     * @param type
     * @param value
     * @return
     */
    private boolean insertData(String type, String value) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(tableColValue, value);
        cv.put(tableColType, type);

        long result = db.insert(tableName, null, cv);

        return result > -1;
    }

    /**
     * Update a row in the database
     * @param type
     * @param value
     * @return
     */
    private boolean updateData(String type, String value){

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(tableColValue, value);

        int result = db.update(tableName, cv, tableColType + " = ?", new String[] {String.valueOf(type) });

        return result > -1;
    }

    /**
     * Fetch a value from the database
     * @param type
     * @return value
     */
    public String fetchType(String type){

        SQLiteDatabase db = this.getWritableDatabase();

        Cursor c = db.rawQuery("SELECT * FROM " + tableName + " WHERE type = ?", new String[] {String.valueOf(type) });


        if(!c.moveToFirst()) {
            c.close();
            return null;
        }

        String val = c.getString(c.getColumnIndex(tableColValue));
        c.close();
        return val;
    }

    /**
     * Update or Insert a value into the database
     * @param type
     * @param value
     */
    public void updateOrInsert(String type, String value){
        if(!insertData(type, value)) {
            updateData(type, value);
        }
    }
}
