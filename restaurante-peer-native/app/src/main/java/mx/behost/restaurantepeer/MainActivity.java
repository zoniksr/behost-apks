package mx.behost.restaurantepeer;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * CUATRO pantallas Android Views reales, sin WebView ni URL embebida.
 * Es un laboratorio de datos ficticios: no procesa caja ni pedidos de producción.
 */
public final class MainActivity extends Activity {
  private final int NAVY=Color.rgb(24,43,74),BLUE=Color.rgb(45,111,222),GREEN=Color.rgb(12,136,106),
    GRAY=Color.rgb(106,126,156),BG=Color.rgb(246,249,254),BORDER=Color.rgb(220,231,245);
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private PeerLedger db;
  private PeerCoordinator peer;
  private JSONObject state;
  private LinearLayout root,content;
  private ScrollView page;
  private EditText hostIp,hostSha,joinCode;
  private String ip="",fingerprint="",pin="",message="No vinculado",role=BuildConfig.ROLE;
  private boolean connected=false,busy=false;
  private static final String[] TAGS={"hostess","mesero","cocina","valet"};

  private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable shape(int color,int radius,int stroke){
    GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
    if(stroke!=0)d.setStroke(dp(1),stroke);return d;
  }
  private String emoji(){switch(role){case "hostess":return "🪑";case "mesero":return "🍽️";case "cocina":return "👨‍🍳";default:return "🚘";}}
  private String roleTitle(){switch(role){case "hostess":return "Hostess";case "mesero":return "Mesero";case "cocina":return "Cocina";default:return "Valet";}}
  private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  private LinearLayout.LayoutParams space(int top){
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;
  }
  private TextView text(String s,int size,int color,boolean bold){
    TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
    if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);
    v.setLineSpacing(dp(3),1f);return v;
  }
  private Button button(String s,boolean primary){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);
    b.setTextColor(primary?Color.WHITE:NAVY);b.setBackground(shape(primary?BLUE:Color.WHITE,14,primary?0:BORDER));
    b.setMinHeight(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));return b;
  }
  private LinearLayout card(){
    LinearLayout c=vertical();c.setPadding(dp(16),dp(15),dp(16),dp(15));
    c.setBackground(shape(Color.WHITE,22,BORDER));content.addView(c,space(12));return c;
  }
  private EditText edit(String hint,String value){
    EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setHint(hint);e.setTextSize(15);
    e.setBackground(shape(Color.WHITE,12,BORDER));e.setPadding(dp(14),dp(10),dp(14),dp(10));return e;
  }
  private void label(LinearLayout l,String title,String detail){
    l.addView(text(title,16,NAVY,true));
    if(detail!=null&&!detail.isEmpty())l.addView(text(detail,12,GRAY,false),space(4));
  }
  private void push(LinearLayout l,View v,int top){l.addView(v,space(top));}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  private void saveFields(){
    if(hostIp!=null)ip=hostIp.getText().toString().trim();
    if(hostSha!=null)fingerprint=hostSha.getText().toString().trim();
    if(joinCode!=null)pin=joinCode.getText().toString().trim();
  }
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    getWindow().setStatusBarColor(Color.rgb(30,72,139));
    getWindow().setNavigationBarColor(Color.rgb(243,247,252));
    try{db=new PeerLedger(this);peer=new PeerCoordinator(this,db);state=db.cachedSnapshot();
        if(state==null)state=db.hostSnapshot();
    }catch(Exception e){new AlertDialog.Builder(this).setTitle("Almacenamiento no disponible").setMessage(
      "No se puede descifrar la base local; no se borrarán datos. "+e.getMessage()
    ).setPositiveButton("Cerrar",(d,w)->finish()).show();return;}
    render();
  }
  private void render(){
    if(db==null)return;
    root=vertical();root.setPadding(dp(14),dp(14),dp(14),dp(24));root.setBackgroundColor(BG);
    page=new ScrollView(this);page.setFillViewport(true);page.addView(root);setContentView(page);
    LinearLayout hero=vertical();hero.setPadding(dp(18),dp(17),dp(18),dp(18));
    GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{Color.rgb(34,81,162),Color.rgb(40,128,189)});
    gradient.setCornerRadius(dp(24));hero.setBackground(gradient);
    root.addView(hero);
    hero.addView(text(emoji()+"   RESTAURANTE BE",14,Color.rgb(219,238,255),true));
    push(hero,text(roleTitle()+" · Android nativo",23,Color.WHITE,true),7);
    push(hero,text("LABORATORIO SIN INTERNET · SOLO DATOS FICTICIOS",11,Color.rgb(255,226,164),true),8);
    String status=peer.active()?"🟢 NODO ACTIVO":"🔵 "+(connected?"CONECTADO AL NODO":"BORRADOR LOCAL");
    LinearLayout info=vertical();info.setPadding(dp(16),dp(14),dp(16),dp(14));info.setBackground(shape(Color.WHITE,18,BORDER));
    push(root,info,12);label(info,status,message);
    push(info,text("Eventos pendientes: "+db.draftsCount(),13,db.draftsCount()>0?Color.rgb(168,104,22):GRAY,true),8);
    Button update=button("↻ Actualizar desde el nodo",false);push(info,update,10);update.setOnClickListener(v->reload());
    Button sync=button("📤 Reconciliar borradores pendientes",false);push(info,sync,8);sync.setOnClickListener(v->syncPrompt());
    LinearLayout setup=vertical();setup.setPadding(dp(16),dp(16),dp(16),dp(17));setup.setBackground(shape(Color.WHITE,20,BORDER));push(root,setup,12);
    label(setup,"🔐 Red interna sin servidor dedicado","Uno de los cuatro teléfonos coordina. Mantén esa app abierta y conecta los demás a la misma Wi-Fi.");
    Button host=button(peer.active()?"⏹ Detener este nodo":"📡 Este dispositivo será el nodo",true);
    push(setup,host,12);host.setOnClickListener(v->{if(peer.active()){peer.stop();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);message="Nodo detenido. No hay coordinador";connected=false;render();}
      else new AlertDialog.Builder(this).setTitle("Iniciar nodo temporal de pruebas").setMessage(
        "No inicies otro nodo mientras el anterior tenga transacciones sin copiar. Este laboratorio NO permite cobros, CFDI ni datos reales. Debes mantener la pantalla encendida."
      ).setNegativeButton("Cancelar",null).setPositiveButton("Iniciar",(d,w)->startHost()).show();});
    if(peer.active()){
      push(setup,text("IP: "+PeerCoordinator.localAddress()+":"+PeerCoordinator.PORT,15,NAVY,true),12);
      push(setup,text("Código temporal: "+peer.code(),15,BLUE,true),6);
      push(setup,text("Huella TLS SHA-256 (comparar en ambas pantallas):",12,GRAY,false),12);
      TextView fp=text(peer.fingerprint(),12,NAVY,true);fp.setTextIsSelectable(true);push(setup,fp,5);
      push(setup,text("El nodo se detiene al cerrar esta app. No existe relevo automático.",12,Color.rgb(162,93,29),false),10);
    }
    hostIp=edit("IP del teléfono anfitrión",ip);push(setup,hostIp,13);
    hostSha=edit("Huella SHA-256 (64 caracteres)",fingerprint);push(setup,hostSha,7);
    joinCode=edit("Código temporal (20 caracteres)",pin);push(setup,joinCode,7);
    Button join=button("🔗 Vincular con nodo",false);push(setup,join,10);
    join.setOnClickListener(v->reload());
    content=vertical();root.addView(content,space(7));
    if(state!=null){
      if("hostess".equals(role))showHostess();
      if("mesero".equals(role))showWaiter();
      if("cocina".equals(role))showKitchen();
      if("valet".equals(role))showValet();
    }
    TextView foot=text("⚠️ No sustituye el sistema actual. Sin servidor coordinador, los cambios son borradores. Nunca marcar pagos o entregas como confirmados sin una respuesta.",12,GRAY,false);
    push(root,foot,20);
  }
  private static JSONObject data(Object...args){
    JSONObject p=new JSONObject();
    try{for(int i=0;i+1<args.length;i+=2)p.put((String)args[i],args[i+1]);}catch(Exception ignored){}
    return p;
  }
  private void startHost(){
    saveFields();
    try{
      peer.start();connected=true;state=peer.localSnapshot();
      message="Solo para QA: este dispositivo coordina por TLS. Mantener abierto.";
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);render();
    }catch(Exception e){message="No pudo iniciar: "+e.getMessage();render();}
  }
  private void reload(){
    saveFields();
    if(peer.active()){
      try{state=peer.localSnapshot();connected=true;message="Consulta local confirmada por el nodo";render();}
      catch(Exception ex){message=ex.getMessage();render();}
      return;
    }
    if(busy)return;busy=true;
    io.execute(()->{
      try{
        JSONObject response=PeerCoordinator.request(ip,fingerprint,pin,data("type","snapshot"));
        db.cache(response.getJSONObject("snapshot"));
        runOnUiThread(()->{busy=false;connected=true;state=response.optJSONObject("snapshot");
          message="Datos recibidos por TLS con huella verificada";render();});
      }catch(Exception ex){
        runOnUiThread(()->{busy=false;connected=false;message="Sin nodo confirmado: "+ex.getMessage();
          try{state=db.cachedSnapshot();if(state==null)state=db.hostSnapshot();}catch(Exception ignored){}
          render();});
      }
    });
  }
  private JSONObject event(String action,JSONObject payload){
    return data("event_id",UUID.randomUUID().toString(),"role",role,"action",action,"payload",payload);
  }
  private void send(String action,JSONObject payload){
    saveFields();
    JSONObject evt=event(action,payload);
    if(peer.active()){
      try{
        JSONObject response=peer.localEvent(evt);state=response.getJSONObject("snapshot");
        connected=true;message="✅ Confirmado en nodo local, evento "+evt.optString("event_id").substring(0,8);render();
      }catch(Exception ex){message="Rechazado por reglas: "+ex.getMessage();render();}
      return;
    }
    if(busy){toast("Termina el envío anterior");return;}busy=true;
    io.execute(()->{
      try{
        JSONObject res=PeerCoordinator.request(ip,fingerprint,pin,data("type","event","event",evt));
        db.cache(res.getJSONObject("snapshot"));
        runOnUiThread(()->{busy=false;connected=true;state=res.optJSONObject("snapshot");
          message="✅ Evento confirmado por nodo local";render();});
      }catch(Exception ex){
        try{
          db.draft(evt);
          runOnUiThread(()->{busy=false;connected=false;message="⚠️ Estado incierto. Borrador conservado con el mismo UUID: "+ex.getMessage();render();});
        }catch(Exception save){
          runOnUiThread(()->{busy=false;message="ERROR CRÍTICO guardando borrador: "+save.getMessage();render();});
        }
      }
    });
  }
  private void syncPrompt(){
    if(db.draftsCount()==0){toast("No hay borradores");return;}
    new AlertDialog.Builder(this).setTitle("Reconciliar con nodo").setMessage(
      "Se reintentará cada operación con su UUID original. Si la mesa o cuenta cambió, el servidor la rechazará. Confirma sólo con el nodo correcto; no se envían cobros."
    ).setNegativeButton("Cancelar",null).setPositiveButton("Revisar y enviar",(d,w)->syncDrafts()).show();
  }
  private void syncDrafts(){
    saveFields();if(busy)return;busy=true;
    io.execute(()->{
      int ok=0;String error="";
      try{
        JSONArray list=db.drafts();
        for(int i=0;i<list.length();i++){
          JSONObject evt=list.getJSONObject(i),reply;
          if(peer.active())reply=peer.localEvent(evt);
          else reply=PeerCoordinator.request(ip,fingerprint,pin,data("type","event","event",evt));
          if(reply.optBoolean("ok")){
            db.cache(reply.getJSONObject("snapshot"));
            db.acknowledge(evt.optString("event_id"));ok++;
          }
        }
      }catch(Exception e){error=e.getMessage();}
      final boolean fullySynced=error.isEmpty();
      final String status=fullySynced?"Conciliado: "+ok+" eventos":"Conciliados "+ok+"; pendiente revisión: "+error;
      runOnUiThread(()->{busy=false;try{state=peer.active()?db.hostSnapshot():db.cachedSnapshot();}catch(Exception ignored){}
        connected=fullySynced;message=status;render();});
    });
  }
  private void section(String title,String help){
    LinearLayout c=card();label(c,title,help);
  }
  private void action(LinearLayout card,String title,Runnable call){
    Button b=button(title,false);push(card,b,9);b.setOnClickListener(v->call.run());
  }
  private void showHostess(){
    section("🪑 Salones y recepción","Mesa, aforo, mesero y estado compartidos por un solo coordinador.");
    JSONArray tables=state.optJSONArray("tables");if(tables==null)return;
    for(int i=0;i<tables.length();i++){
      JSONObject t=tables.optJSONObject(i);if(t==null)continue;
      LinearLayout c=card();
      String status=t.optString("state"),name=t.optString("name"),area=t.optString("area");
      label(c,("libre".equals(status)?"🟢 ":"ocupada".equals(status)?"🔵 ":"🟠 ")+name+" · "+area,
        t.optInt("guests")+"/"+t.optInt("seats")+" personas · "+status);
      if("ocupada".equals(status)){String visit=t.optString("visit");push(c,text("👤 Mesero: "+t.optString("waiter")+" · Visita "+visit.substring(0,Math.min(8,visit.length())),12,GRAY,false),6);}
      if("libre".equals(status))action(c,"＋ Sentar y asignar mesero",()->seat(t));
      if("ocupada".equals(status)){
        action(c,"🔄 Relevar mesero",()->handoff(t));
        action(c,"↗ Cambiar mesa",()->move(t));
      }
      if("limpieza".equals(status))action(c,"🧽 Confirmar limpieza",()->send("clean",data("table_id",t.optString("id"),"rev",t.optInt("rev"))));
    }
  }
  private LinearLayout form(){LinearLayout l=vertical();l.setPadding(dp(18),dp(6),dp(18),dp(8));return l;}
  private void seat(JSONObject table){
    LinearLayout f=form();EditText guests=edit("Personas","2"),waiter=edit("Mesero de turno","Mesero de muestra");
    guests.setInputType(2);f.addView(guests);push(f,waiter,9);
    new AlertDialog.Builder(this).setTitle("🪑 "+table.optString("name")).setView(f).setNegativeButton("Cancelar",null)
      .setPositiveButton("Confirmar",(d,w)->{
        try{send("seat",data("table_id",table.optString("id"),"guests",Integer.parseInt(guests.getText().toString()),
          "waiter",waiter.getText().toString(),"rev",table.optInt("rev")));}
        catch(Exception ex){toast("Revisa aforo y responsable");}
      }).show();
  }
  private void handoff(JSONObject table){
    LinearLayout f=form();EditText waiter=edit("Nuevo mesero",""),reason=edit("Motivo obligatorio","");
    push(f,waiter,5);push(f,reason,8);
    new AlertDialog.Builder(this).setTitle("🔄 Relevo de "+table.optString("name")).setView(f)
      .setNegativeButton("Volver",null).setPositiveButton("Registrar",(d,w)->send("handoff",
        data("table_id",table.optString("id"),"waiter",waiter.getText().toString(),"reason",reason.getText().toString(),
          "rev",table.optInt("rev")))).show();
  }
  private void move(JSONObject source){
    JSONArray all=state.optJSONArray("tables");if(all==null)return;
    ArrayList<JSONObject> free=new ArrayList<>();ArrayList<String> titles=new ArrayList<>();
    for(int i=0;i<all.length();i++){
      JSONObject t=all.optJSONObject(i);
      if(t!=null&&"libre".equals(t.optString("state"))&&t.optInt("seats")>=source.optInt("guests")){
        free.add(t);titles.add(t.optString("name")+" · "+t.optString("area"));
      }
    }
    if(free.isEmpty()){toast("No hay mesa destino disponible");return;}
    new AlertDialog.Builder(this).setTitle("Mover visita (mantener historial)").setItems(titles.toArray(new String[0]),(d,index)->{
      JSONObject target=free.get(index);
      new AlertDialog.Builder(this).setTitle("Confirmar traslado").setMessage(
        source.optString("name")+" → "+target.optString("name")+". El origen pasa a limpieza y se conserva la visita."
      ).setNegativeButton("Volver",null).setPositiveButton("Trasladar",(di,w)->send("move",
        data("table_id",source.optString("id"),"dest_id",target.optString("id"),"rev",source.optInt("rev"),
          "dest_rev",target.optInt("rev")))).show();
    }).show();
  }
  private void showWaiter(){
    section("🍽️ Mis comandas","Los pedidos se marcan recibidos sólo con acuse del nodo.");
    JSONArray all=state.optJSONArray("tables"),menu=state.optJSONArray("menu");if(all==null||menu==null)return;
    for(int i=0;i<all.length();i++){
      JSONObject t=all.optJSONObject(i);
      if(t==null||!"ocupada".equals(t.optString("state")))continue;
      LinearLayout c=card();label(c,"🪑 "+t.optString("name"),"Mesero: "+t.optString("waiter")+" · "+t.optInt("guests")+" personas");
      action(c,"＋ Enviar comanda de muestra",()->newOrder(t,menu));
    }
  }
  private void newOrder(JSONObject t,JSONArray menu){
    String[] labels=new String[menu.length()];
    for(int i=0;i<menu.length();i++)labels[i]=menu.optJSONObject(i).optString("name");
    new AlertDialog.Builder(this).setTitle("Selecciona producto · DEMO").setItems(labels,(d,index)->{
      JSONObject item=menu.optJSONObject(index);
      send("order",data("table_id",t.optString("id"),"product_id",item.optString("id"),"qty",1,"rev",t.optInt("rev")));
    }).show();
  }
  private void showKitchen(){
    section("👨‍🍳 Cocina en vivo","Las comandas tienen UUID único; no se duplican tras un timeout.");
    JSONArray orders=state.optJSONArray("orders");
    if(orders==null||orders.length()==0){section("Sin comandas","Sentar una mesa y hacer un pedido desde Mesero.");return;}
    for(int i=0;i<orders.length();i++){
      JSONObject o=orders.optJSONObject(i);if(o==null)continue;
      LinearLayout c=card();label(c,"🍳 "+o.optString("name")+" × "+o.optInt("qty"),
        o.optString("table_id")+" · "+o.optString("status"));
      if("enviado".equals(o.optString("status")))action(c,"✅ Marcar listo",(()->send("kitchen_ready",data("order_id",o.optString("id")))));
    }
  }
  private void showValet(){
    section("🚘 Recepción de vehículos","Sin confirmación de nodo, una ficha permanece como borrador.");
    LinearLayout form=card();EditText plate=edit("Placas",""),color=edit("Color","");
    push(form,plate,4);push(form,color,8);
    Button add=button("＋ Registrar recepción",true);push(form,add,10);
    add.setOnClickListener(v->send("valet_add",data("plate",plate.getText().toString(),"color",color.getText().toString())));
    JSONArray cars=state.optJSONArray("valet");if(cars==null)return;
    for(int i=0;i<cars.length();i++){
      JSONObject car=cars.optJSONObject(i);if(car==null)continue;
      LinearLayout c=card();label(c,"🚙 "+car.optString("plate"),car.optString("color")+" · "+car.optString("state"));
      if("recibido".equals(car.optString("state")))action(c,"🔑 Confirmar entrega",()->
        new AlertDialog.Builder(this).setTitle("Identidad y ficha").setMessage(
          "Comprueba presencialmente placas, ficha y persona autorizada."
        ).setNegativeButton("Volver",null).setPositiveButton("Entregado",(d,w)->send("valet_deliver",
          data("valet_id",car.optString("id"),"identity_confirmed",true))).show());
    }
  }
  @Override public void onDestroy(){
    if(peer!=null)peer.stop();
    io.shutdownNow();super.onDestroy();
  }
}
