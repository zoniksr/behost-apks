package mx.behost.restaurantepeer;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.JSONObject;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/**
 * Nodo efimero, TLS 1.2+ con certificado generado en AndroidKeyStore.
 * El cliente exige SHA-256 verificado presencialmente y codigo de sesion.
 * La app anfitriona debe permanecer abierta; NO tiene eleccion automatica.
 */
public final class PeerCoordinator {
  public static final int PORT=39217;
  private static final String ALIAS="restaurante-be-qa-peer-tls";
  private final PeerLedger ledger;
  private final Context context;
  private volatile SSLServerSocket listener;
  private volatile String code="";
  private final ExecutorService workers=Executors.newFixedThreadPool(4);
  private ExecutorService accepting=Executors.newSingleThreadExecutor();
  private String fingerprint="";
  private static final SecureRandom rng=new SecureRandom();

  public PeerCoordinator(Context ctx,PeerLedger db){context=ctx.getApplicationContext();ledger=db;}
  private static String hex(byte[] b){
    StringBuilder s=new StringBuilder();
    for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
    return s.toString();
  }
  private static String sha(byte[] data) throws Exception {
    return hex(MessageDigest.getInstance("SHA-256").digest(data));
  }
  private KeyStore keys() throws Exception {
    KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
    if(!ks.containsAlias(ALIAS)){
      KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
      Date begin=new Date(System.currentTimeMillis()-86400000L);
      Date end=new Date(System.currentTimeMillis()+10L*365*24*60*60*1000);
      gen.initialize(new KeyGenParameterSpec.Builder(ALIAS,
        KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .setCertificateSubject(new X500Principal("CN=RestauranteBeNodoQA"))
        .setCertificateSerialNumber(new BigInteger(64,rng).abs().add(BigInteger.ONE))
        .setCertificateNotBefore(begin).setCertificateNotAfter(end).build());
      gen.generateKeyPair();
    }
    return ks;
  }
  public synchronized void start() throws Exception {
    if(listener!=null)throw new IllegalStateException("El nodo ya esta activo");
    KeyStore ks=keys();
    fingerprint=sha(ks.getCertificate(ALIAS).getEncoded());
    KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(ks,null);
    SSLContext ssl=SSLContext.getInstance("TLS");
    ssl.init(km.getKeyManagers(),null,rng);
    SSLServerSocket server=(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket(PORT);
    server.setReuseAddress(true);
    server.setEnabledProtocols(new String[]{"TLSv1.2","TLSv1.3"});
    listener=server;
    byte[] secret=new byte[10];rng.nextBytes(secret);code=hex(secret);
    accepting.execute(()->{
      while(listener==server&&!server.isClosed()){
        try {
          final SSLSocket conn=(SSLSocket)server.accept();
          conn.setSoTimeout(6500);
          workers.execute(()->serve(conn));
        }catch(Exception e){if(!server.isClosed())android.util.Log.w("BeNodoQA","Conexion rechazada",e);}
      }
    });
  }
  private void serve(SSLSocket socket){
    try(SSLSocket s=socket;
        BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8))){
      s.startHandshake();
      String message=r.readLine();
      if(message==null||message.length()>14000)throw new SecurityException("Mensaje demasiado largo");
      JSONObject req=new JSONObject(message);
      String provided=req.optString("code","");
      if(!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)))
        throw new SecurityException("Codigo de vinculacion incorrecto");
      JSONObject response;
      if("snapshot".equals(req.optString("type"))){
        response=new JSONObject().put("ok",true).put("snapshot",ledger.hostSnapshot());
      }else if("event".equals(req.optString("type"))){
        JSONObject evt=req.optJSONObject("event");
        if(evt==null)throw new IllegalArgumentException("Evento obligatorio");
        response=ledger.apply(evt);
      }else throw new IllegalArgumentException("Operacion desconocida");
      String json=response.toString();
      if(json.length()>500000)throw new IllegalStateException("Respuesta excesiva");
      w.write(json);w.write('\n');w.flush();
    }catch(Exception e){android.util.Log.w("BeNodoQA","Solicitud no confirmada: "+e.getMessage());}
  }
  public synchronized void stop(){
    SSLServerSocket s=listener;listener=null;code="";
    if(s!=null)try{s.close();}catch(Exception ignored){}
  }
  public boolean active(){return listener!=null&&!listener.isClosed();}
  public String code(){return code;}
  public String fingerprint(){return fingerprint;}
  public static String localAddress(){
    try{
      Enumeration<NetworkInterface> n=NetworkInterface.getNetworkInterfaces();
      while(n.hasMoreElements()){
        NetworkInterface nic=n.nextElement();
        if(!nic.isUp()||nic.isLoopback())continue;
        Enumeration<InetAddress> a=nic.getInetAddresses();
        while(a.hasMoreElements()){
          InetAddress ip=a.nextElement();
          if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())return ip.getHostAddress();
        }
      }
    }catch(Exception ignored){}
    return "(sin IPv4 LAN)";
  }
  public static JSONObject request(String host,String fingerprint,String pin,JSONObject body) throws Exception {
    if(host==null||!host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}"))throw new IllegalArgumentException("Escribe la IP de la red local");
    InetAddress address=InetAddress.getByName(host);
    if(!(address instanceof Inet4Address)||!address.isSiteLocalAddress())
      throw new SecurityException("Solo conexiones IPv4 privadas de LAN");
    String expected=fingerprint==null?"":fingerprint.replace(":","").replace(" ","").toLowerCase(java.util.Locale.ROOT);
    if(!expected.matches("[a-f0-9]{64}"))throw new SecurityException("Compara y captura los 64 caracteres SHA-256 del anfitrion");
    if(pin==null||!pin.matches("[a-f0-9]{20}"))throw new SecurityException("Codigo temporal invalido");
    X509TrustManager pinned=new X509TrustManager(){
      public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
      public void checkClientTrusted(X509Certificate[] chain,String auth) throws CertificateException {
        throw new CertificateException("Clientes TLS no admitidos en el nodo");
      }
      public void checkServerTrusted(X509Certificate[] chain,String auth) throws CertificateException {
        try{
          if(chain==null||chain.length<1)throw new CertificateException("Certificado ausente");
          String got=sha(chain[0].getEncoded());
          if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),got.getBytes(StandardCharsets.US_ASCII)))
            throw new CertificateException("Huella TLS no coincide");
        }catch(CertificateException ex){throw ex;}
        catch(Exception ex){throw new CertificateException("Certificado invalido",ex);}
      }
    };
    SSLContext tls=SSLContext.getInstance("TLS");
    tls.init(null,new TrustManager[]{pinned},rng);
    try(SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket()){
      socket.connect(new InetSocketAddress(address,PORT),5000);
      socket.setSoTimeout(8000);
      socket.setEnabledProtocols(new String[]{"TLSv1.2","TLSv1.3"});
      socket.startHandshake();
      body.put("code",pin);
      try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
          BufferedReader r=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))){
        w.write(body.toString());w.write('\n');w.flush();
        String reply=r.readLine();
        if(reply==null||reply.length()>500000)throw new IOException("Sin confirmacion del nodo");
        JSONObject response=new JSONObject(reply);
        if(!response.optBoolean("ok"))throw new IllegalStateException(response.optString("error","Rechazada"));
        return response;
      }
    }
  }
  public JSONObject localEvent(JSONObject event) throws Exception {
    if(!active())throw new IllegalStateException("Nodo inactivo");
    return ledger.apply(event);
  }
  public JSONObject localSnapshot() throws Exception {return ledger.hostSnapshot();}
}
