package mx.behost.restaurantepeer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Persistencia de laboratorio. WAL + UUID idempotente + revision por mesa.
 * Nunca expone pagos, CFDI, inventario ni datos de clientes reales.
 */
public final class PeerLedger extends SQLiteOpenHelper {
  private static final String KEY_ALIAS="rb_peer_qa_ledger_1";
  private final SecretKey key;

  public PeerLedger(Context c) throws Exception {
    super(c,"rb_peer_demo_v1.db",null,1);
    setWriteAheadLoggingEnabled(true);
    KeyStore keys=KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
    if(!keys.containsAlias(KEY_ALIAS)){
      KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
      gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true).build());
      gen.generateKey();
    }
    key=((KeyStore.SecretKeyEntry)keys.getEntry(KEY_ALIAS,null)).getSecretKey();
    SQLiteDatabase db=getWritableDatabase();
    try(Cursor c0=db.rawQuery("SELECT count(*) FROM snapshots WHERE kind='host'",null)){
      if(c0.moveToFirst()&&c0.getInt(0)==0){putSnapshot(db,"host",seed());}
    }
  }
  @Override public void onCreate(SQLiteDatabase db){
    db.execSQL("CREATE TABLE snapshots(kind TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_ms INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE events(event_id TEXT PRIMARY KEY, response TEXT NOT NULL, created_ms INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE drafts(event_id TEXT PRIMARY KEY, payload TEXT NOT NULL, created_ms INTEGER NOT NULL)");
  }
  @Override public void onUpgrade(SQLiteDatabase db,int from,int to){
    throw new IllegalStateException("Migracion destructiva no permitida");
  }
  private String seal(String plain) throws Exception {
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);
    c.updateAAD("RESTAURANTE-BE-PEER-QA".getBytes("UTF-8"));
    byte[] iv=c.getIV(),encrypted=c.doFinal(plain.getBytes("UTF-8")),out=new byte[iv.length+encrypted.length];
    System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(encrypted,0,out,iv.length,encrypted.length);
    return Base64.encodeToString(out,Base64.NO_WRAP);
  }
  private String open(String code) throws Exception {
    byte[] b=Base64.decode(code,Base64.DEFAULT);if(b.length<29)throw new SecurityException("Dato incompleto");
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,b,0,12));
    c.updateAAD("RESTAURANTE-BE-PEER-QA".getBytes("UTF-8"));
    return new String(c.doFinal(b,12,b.length-12),"UTF-8");
  }
  private JSONObject from(SQLiteDatabase db,String kind) throws Exception {
    try(Cursor c=db.rawQuery("SELECT payload FROM snapshots WHERE kind=?",new String[]{kind})){
      if(!c.moveToFirst())return null;
      return new JSONObject(open(c.getString(0)));
    }
  }
  private void putSnapshot(SQLiteDatabase db,String kind,JSONObject value) throws Exception {
    ContentValues v=new ContentValues();
    v.put("kind",kind);v.put("payload",seal(value.toString()));v.put("updated_ms",System.currentTimeMillis());
    db.insertWithOnConflict("snapshots",null,v,SQLiteDatabase.CONFLICT_REPLACE);
  }
  public synchronized JSONObject hostSnapshot() throws Exception {return from(getReadableDatabase(),"host");}
  public synchronized JSONObject cachedSnapshot() throws Exception {return from(getReadableDatabase(),"client");}
  public synchronized void cache(JSONObject json) throws Exception {putSnapshot(getWritableDatabase(),"client",json);}
  public synchronized int draftsCount(){
    try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM drafts",null)){
      return c.moveToFirst()?c.getInt(0):0;
    }
  }
  public synchronized void draft(JSONObject event) throws Exception {
    String id=event.optString("event_id");
    if(!id.matches("[a-f0-9\\-]{36}"))throw new IllegalArgumentException("UUID invalido");
    ContentValues v=new ContentValues();
    v.put("event_id",id);v.put("payload",seal(event.toString()));v.put("created_ms",System.currentTimeMillis());
    getWritableDatabase().insertWithOnConflict("drafts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
  }
  public synchronized JSONArray drafts() throws Exception {
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM drafts ORDER BY created_ms ASC LIMIT 100",null)){
      while(c.moveToNext())out.put(new JSONObject(open(c.getString(0))));
    }
    return out;
  }
  public synchronized void acknowledge(String id){
    getWritableDatabase().delete("drafts","event_id=?",new String[]{id});
  }
  private JSONObject seed() throws Exception {
    JSONObject s=new JSONObject();s.put("demo",true);s.put("version",0);s.put("label","RESTAURANTE BE · DATOS DE MUESTRA");
    JSONArray tables=new JSONArray();
    for(int i=1;i<=18;i++){
      JSONObject t=new JSONObject();t.put("id","mesa-"+i).put("name","Mesa "+i).put("area",i<=10?"Salon":"Terraza");
      t.put("seats",i<=10?4:2).put("guests",0).put("state","libre").put("waiter","").put("visit","").put("rev",0);
      tables.put(t);
    }
    JSONArray menu=new JSONArray();
    menu.put(new JSONObject().put("id","demo-1").put("name","Ensalada de muestra").put("price_cents",14500));
    menu.put(new JSONObject().put("id","demo-2").put("name","Cafe de muestra").put("price_cents",4500));
    menu.put(new JSONObject().put("id","demo-3").put("name","Plato de muestra").put("price_cents",22000));
    s.put("tables",tables).put("menu",menu).put("orders",new JSONArray()).put("valet",new JSONArray());
    return s;
  }
  private static JSONObject find(JSONArray a,String id){
    for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null&&id.equals(j.optString("id")))return j;}return null;
  }
  private static String string(JSONObject p,String key,int max){
    String v=p.optString(key,"").trim();
    if(v.length()>max)throw new IllegalArgumentException(key+": muy largo");
    return v;
  }
  private static void require(boolean condition,String message){
    if(!condition)throw new IllegalArgumentException(message);
  }
  private static void role(String role,String... allowed){
    for(String r:allowed)if(r.equals(role))return;
    throw new SecurityException("Este rol no puede realizar esa accion");
  }
  private static JSONObject table(JSONObject s,String id){
    JSONObject t=find(s.optJSONArray("tables"),id);
    if(t==null)throw new IllegalArgumentException("Mesa inexistente");
    return t;
  }
  private static void rev(JSONObject t,JSONObject p,String field){
    require(t.optInt("rev")==p.optInt(field,-1),"Mesa modificada por otro equipo. Actualiza y reintenta.");
  }
  /**
   * El nodo unico aplica eventos atomicos; UUID impide duplicar cuando una red se corta.
   */
  public synchronized JSONObject apply(JSONObject event) throws Exception {
    String eid=event.optString("event_id"),actor=event.optString("role"),action=event.optString("action");
    require(eid.matches("[a-f0-9\\-]{36}"),"UUID de evento invalido");
    require(event.toString().length()<12000,"Evento demasiado largo");
    JSONObject p=event.optJSONObject("payload");require(p!=null,"Faltan datos");
    SQLiteDatabase db=getWritableDatabase();
    db.beginTransaction();
    try {
      try(Cursor c=db.rawQuery("SELECT response FROM events WHERE event_id=?",new String[]{eid})){
        if(c.moveToFirst()){
          JSONObject reply=new JSONObject(open(c.getString(0)));
          reply.put("replayed",true);db.setTransactionSuccessful();return reply;
        }
      }
      JSONObject state=from(db,"host");require(state!=null&&state.optBoolean("demo"),"Nodo sin datos");
      JSONArray orders=state.getJSONArray("orders");
      JSONArray valet=state.getJSONArray("valet");
      JSONObject changed=null;
      switch(action){
        case "seat":{
          role(actor,"hostess");
          JSONObject t=table(state,string(p,"table_id",40));rev(t,p,"rev");
          require("libre".equals(t.optString("state")),"Mesa no disponible");
          int n=p.optInt("guests",-1);
          require(n>0&&n<=t.optInt("seats"),"Capacidad excedida");
          String waiter=string(p,"waiter",100);require(!waiter.isEmpty(),"Asignar mesero");
          t.put("guests",n).put("state","ocupada").put("waiter",waiter)
            .put("visit",UUID.randomUUID().toString()).put("rev",t.getInt("rev")+1);
          changed=t;break;
        }
        case "handoff":{
          role(actor,"hostess");
          JSONObject t=table(state,string(p,"table_id",40));rev(t,p,"rev");
          require("ocupada".equals(t.optString("state")),"Mesa sin visita");
          String waiter=string(p,"waiter",100),reason=string(p,"reason",200);
          require(!waiter.isEmpty()&&reason.length()>=4,"Mesero nuevo y motivo obligatorios");
          t.put("waiter",waiter).put("rev",t.getInt("rev")+1);
          changed=t;break;
        }
        case "move":{
          role(actor,"hostess");
          JSONObject source=table(state,string(p,"table_id",40)),dest=table(state,string(p,"dest_id",40));
          require(source!=dest,"Destino igual al origen");
          rev(source,p,"rev");rev(dest,p,"dest_rev");
          require("ocupada".equals(source.optString("state"))&&"libre".equals(dest.optString("state")),"Origen o destino ocupados");
          require(source.optInt("guests")<=dest.optInt("seats"),"Destino sin capacidad");
          dest.put("state","ocupada").put("guests",source.optInt("guests")).put("waiter",source.optString("waiter"))
            .put("visit",source.optString("visit")).put("rev",dest.optInt("rev")+1);
          source.put("state","limpieza").put("guests",0).put("waiter","").put("visit","").put("rev",source.optInt("rev")+1);
          changed=dest;break;
        }
        case "clean":{
          role(actor,"hostess");
          JSONObject t=table(state,string(p,"table_id",40));rev(t,p,"rev");
          require("limpieza".equals(t.optString("state")),"Mesa no requiere limpieza");
          t.put("state","libre").put("rev",t.optInt("rev")+1);changed=t;break;
        }
        case "order":{
          role(actor,"mesero");
          JSONObject t=table(state,string(p,"table_id",40));rev(t,p,"rev");
          require("ocupada".equals(t.optString("state")),"Mesa sin visita");
          JSONObject dish=find(state.getJSONArray("menu"),string(p,"product_id",40));require(dish!=null,"Producto no disponible");
          int qty=p.optInt("qty",-1);require(qty>0&&qty<=15,"Cantidad no valida");
          JSONObject o=new JSONObject().put("id",UUID.randomUUID().toString()).put("visit",t.optString("visit"))
            .put("table_id",t.optString("id")).put("name",dish.optString("name")).put("price_cents",dish.optInt("price_cents"))
            .put("qty",qty).put("status","enviado").put("waiter",t.optString("waiter"));
          orders.put(o);t.put("rev",t.optInt("rev")+1);changed=o;break;
        }
        case "kitchen_ready":{
          role(actor,"cocina");
          JSONObject o=find(orders,string(p,"order_id",70));
          require(o!=null&&"enviado".equals(o.optString("status")),"Comanda ya atendida o no encontrada");
          o.put("status","listo");changed=o;break;
        }
        case "valet_add":{
          role(actor,"valet");
          String plate=string(p,"plate",16).toUpperCase(java.util.Locale.ROOT),color=string(p,"color",40);
          require(plate.length()>=4&&color.length()>=2,"Verifica placas y color");
          JSONObject v=new JSONObject().put("id",UUID.randomUUID().toString()).put("plate",plate)
            .put("color",color).put("state","recibido");
          valet.put(v);changed=v;break;
        }
        case "valet_deliver":{
          role(actor,"valet");
          JSONObject v=find(valet,string(p,"valet_id",70));require(v!=null&&"recibido".equals(v.optString("state")),"Auto no disponible");
          require(p.optBoolean("identity_confirmed",false),"Verifica identidad y ficha");
          v.put("state","entregado");changed=v;break;
        }
        default:throw new IllegalArgumentException("Accion no admitida en este laboratorio");
      }
      state.put("version",state.optLong("version")+1);
      putSnapshot(db,"host",state);
      JSONObject response=new JSONObject().put("ok",true).put("event_id",eid).put("replayed",false)
        .put("version",state.optLong("version")).put("item",changed).put("snapshot",state);
      ContentValues v=new ContentValues();v.put("event_id",eid);v.put("response",seal(response.toString()));v.put("created_ms",System.currentTimeMillis());
      db.insertOrThrow("events",null,v);db.setTransactionSuccessful();return response;
    }finally{db.endTransaction();}
  }
}
