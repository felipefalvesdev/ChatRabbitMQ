package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.*;
import java.text.*;
import java.io.FileOutputStream;
import java.nio.file.*;
import java.io.File;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import org.json.simple.*;
import org.json.simple.parser.*;

public class Chat {

  private static final DateFormat HORA = new SimpleDateFormat("HH:mm");
  private static final DateFormat DATA = new SimpleDateFormat("dd/MM/yyyy");
  static String user_Destination = "";
  static Calendar calendario = null;

  public static void main(String[] argv) throws Exception {
    Scanner ler = new Scanner(System.in);
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUri("amqp://adm:rabbit@AMQP-LB-335a33e4e7b1ec32.elb.us-east-1.amazonaws.com");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();//fila para mensagens comuns
    Channel channel_file = connection.createChannel(); //canal para fila de arquivos
    System.out.print("User: ");
    final String NAME_QUEUE = ler.nextLine();
    createDir(NAME_QUEUE); //cria pasta para guardar downloads do usuario
    String NAME_QUEUE_files = NAME_QUEUE + "F";
    channel.queueDeclare(NAME_QUEUE, false, false, false, null);//cria fila normal
    channel_file.queueDeclare(NAME_QUEUE_files, false, false, false, null);//cria fila de arquivos
    String menssagem = "";

    Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        System.out.println("");
        try {
          System.out.println(receberMenssagem(body, NAME_QUEUE));
        } catch (Exception e) {
          System.out.println("ERRO!");
        }
        System.out.print(user_Destination + ">>");
      }
    };
    channel.basicConsume(NAME_QUEUE, true, consumer);
    channel_file.basicConsume(NAME_QUEUE_files, true, consumer);

    String grupo = "";
    while (menssagem.equals("=.=") == false) {
      System.out.print(user_Destination + ">>");
      menssagem = ler.nextLine();
      if (menssagem.equals(".=") == true) {
        break;
      }
      if (menssagem.charAt(0) == '@') {//Muda usuario do prompt
        user_Destination = menssagem;
        grupo = "";
      } else if (menssagem.charAt(0) == '#') {
        user_Destination = menssagem;
        grupo = user_Destination.substring(1);
      } else if (menssagem.charAt(0) == '!') {
        //criando novo grupo
        if (menssagem.contains("addGroup")) {
          addGroup(menssagem, channel, NAME_QUEUE);
        } //!addUser nome_de_usuario nome_do_grupo
        else if (menssagem.contains("addUser")) {
          addUser(menssagem, channel);
        } //!delFromGroup nome_de_usuario nome_do_grupo
        else if (menssagem.contains("delFromGroup")) {
          delFromGroup(menssagem, channel);
        } //!removeGroup nome_do_grupo
        else if (menssagem.contains("removeGroup")) {
          removeGroup(menssagem, channel);
        } //listGroup
        else if (menssagem.contains("listGroups")) {
          String path = "/api/queues/%2F/" + NAME_QUEUE + "/bindings";
          REST(path, "source");
        } //!listUsers nome_do_grupo
        else if (menssagem.contains("listUsers")) {
          String group = menssagem.substring(11);
          String path = "/api/exchanges/%2F/" + group + "/bindings/source";
          REST(path, "destination");
        } //!upload
        else if (menssagem.contains("upload")) {
          String destination = user_Destination.substring(1); //remove o '@' ou '#'
          String fileName = menssagem.substring(8);
          System.out.println("Enviando \"" + fileName + "\" para " + user_Destination + ".");
          uploadFile(fileName, destination, channel_file, NAME_QUEUE, grupo);
        }
      } else if (user_Destination.equals("") == false) {
        if (user_Destination.charAt(0) == '#')//caso seja para um grupo
        {
          enviarMenssagem(NAME_QUEUE, menssagem, "", channel, grupo);
        } else {
          enviarMenssagem(NAME_QUEUE, menssagem, user_Destination.substring(1), channel, ""); //caso seja para um grupo
        }
      }
    }
    channel.close();
    connection.close();

  }
  //Adiciona um usuario a um grupo

  static void addUser(String menssagem, Channel channel) throws Exception {
    String novaMenssagem = menssagem.substring(9);//pega restante da string, apos o comando addUser
    String user_name = novaMenssagem.substring(0, novaMenssagem.indexOf(" "));//pega nome usuario
    String groupName = novaMenssagem.substring(novaMenssagem.indexOf(" ") + 1);//obtem nome grupo
    channel.exchangeDeclare(groupName, "fanout");
    channel.exchangeDeclare(groupName + "F", "fanout");
    try {//caso a fila nao exista
      channel.queueBind(user_name, groupName, "");
      channel.queueBind(user_name + "F", groupName + "F", "");
    } catch (Exception e) {
    }
  }

  //Cria um grupo
  static void addGroup(String menssagem, Channel channel, String criador) throws Exception {
    String groupName = menssagem.substring(10);
    channel.exchangeDeclare(groupName, "fanout");
    channel.queueBind(criador, groupName, ""); //Adiciona o usuario criador ao grupo.
    channel.exchangeDeclare(groupName + "F", "fanout");
    channel.queueBind(criador + "F", groupName + "F", "");
  }
  //Remove um grupo

  static void removeGroup(String menssagem, Channel channel) throws Exception {
    String groupName = menssagem.substring(13);
    channel.exchangeDelete(groupName);
    channel.exchangeDelete(groupName + "F");
  }

  static void enviarMenssagem(String NAME_QUEUE, String menssagem, String user_Destination, Channel channel, String grupo) throws Exception {
    //Criando Mensagem
    MensagemProto.Mensagem.Builder pacote = MensagemProto.Mensagem.newBuilder();
    calendario = Calendar.getInstance();
    pacote.setEmissor(NAME_QUEUE);
    pacote.setData((DATA.format(calendario.getTime())));
    pacote.setHora(HORA.format(calendario.getTime()));
    pacote.setGrupo(grupo);
    MensagemProto.Conteudo.Builder conteudo = MensagemProto.Conteudo.newBuilder();
    ByteString bytstring = ByteString.copyFrom(menssagem.getBytes("UTF-8"));
    conteudo.setCorpo(bytstring);
    conteudo.setTipo("");
    conteudo.setNome("");
    pacote.setConteudo(conteudo);
    MensagemProto.Mensagem msg = pacote.build();
    byte[] buffer = msg.toByteArray();
    channel.basicPublish(grupo, user_Destination, null, buffer);
  }

  static void delFromGroup(String menssagem, Channel channel) throws Exception {
    String novaMenssagem = menssagem.substring(14);
    String user_name = novaMenssagem.substring(0, novaMenssagem.indexOf(" "));//nome usuario
    String groupName = novaMenssagem.substring(novaMenssagem.indexOf(" ") + 1);//nome grupo
    try {//caso a fila nao exista
      channel.queueUnbind(user_name, groupName, "");
      channel.queueUnbind(user_name + "F", groupName + "F", "");
    } catch (Exception e) {
    }
  }

  static String receberMenssagem(byte[] pacote, String user) throws Exception {
    MensagemProto.Mensagem msg = MensagemProto.Mensagem.parseFrom(pacote);
    String emissor = msg.getEmissor();
    String data = msg.getData();
    String hora = msg.getHora();
    String grupo = msg.getGrupo();
    MensagemProto.Conteudo conteudo = msg.getConteudo();
    String nome = conteudo.getNome();
    String retorno = "";
    if (nome.equals("") == false) {//se for um arquivo
      FileDownloader downloader = new FileDownloader(user, conteudo, emissor, data, hora, grupo);
    } else {
      ByteString corpo = conteudo.getCorpo();
      String info = corpo.toStringUtf8();

      if (grupo.equals("") == false) {
        grupo = "#" + grupo; //se msg for para um grupo;
      } else {
        emissor = "@" + emissor;
      }

      retorno = "(" + data + " às " + hora + ") " + emissor + grupo + " diz: " + info; //caso seja para um grupo
    }
    return retorno;
  }

  //Faz upload do arquivo
  static void uploadFile(String file_name, String destination, Channel channel, String sender, String group) {
    calendario = Calendar.getInstance();
    String date = DATA.format(calendario.getTime());
    String time = HORA.format(calendario.getTime());
    FileUploader uploader = new FileUploader(file_name, destination, sender, channel, date, time, group);
  }

  //cria um diretorio
  static void createDir(String user) {
    new File(user + "_download").mkdir();
  }

  private static void getName(JSONObject novoObjeto, String option) {
    String str = (String) novoObjeto.get(option);
    if (str.equals("") == false) {
      System.out.print("\"" + str + "\"" + "  ");
    }
  }

  static void REST(String Path, String option) {
    try {
      String username = "adm";
      String password = "rabbit";
      String usernameAndPassword = username + ":" + password;
      String authorizationHeaderName = "Authorization";
      String authorizationHeaderValue = "Basic " + java.util.Base64.getEncoder().encodeToString(usernameAndPassword.getBytes());

      // Perform a request
      String restResource = "http://HTTP-LB-a23fad07e62c46d4.elb.us-east-1.amazonaws.com";
      Client client = ClientBuilder.newClient();
      Response resposta = client.target(restResource)
              .path(Path)
              .request(MediaType.APPLICATION_JSON)
              .header(authorizationHeaderName, authorizationHeaderValue) // The basic authentication header goes here
              .get();     // Perform a post with the form values

      if (resposta.getStatus() == 200) {
        String json = resposta.readEntity(String.class);
        JSONParser jsonParser = new JSONParser();
        Object novoObjeto = jsonParser.parse(json);
        JSONArray array1 = (JSONArray) novoObjeto;
        array1.forEach(elem -> getName((JSONObject) elem, option));
      }
      System.out.println("");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
