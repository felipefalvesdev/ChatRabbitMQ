package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.*;
import java.text.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class FileUploader extends Thread{
    private final String nome_arquivo;
    private final String destination;
    private final String sender;
    private final String grupo;
    private final Channel channel;
    private final String date;
    private final String time;

    public FileUploader(String nome_arquivo, String destination, String sender, Channel channel, String date, String time, String grupo){
        this.nome_arquivo = nome_arquivo;
        this.destination = destination;
        this.sender = sender;
        this.grupo = grupo;
        this.channel = channel;
        this.date = date;
        this.time = time;
        start();
    }

    public void run(){
        try{
            //Obtendo arquivo
            Path file_path = Paths.get(this.nome_arquivo);
            byte[] file = Files.readAllBytes(file_path);


            //Criando Mensagem
            MensagemProto.Mensagem.Builder pacote = MensagemProto.Mensagem.newBuilder();
            pacote.setEmissor(this.sender);
            pacote.setData(this.date);
            pacote.setHora(this.time);
            pacote.setGrupo(this.grupo);

            //Criando Conteudo
            MensagemProto.Conteudo.Builder conteudo = MensagemProto.Conteudo.newBuilder();
            ByteString bstring = ByteString.copyFrom(file);

            conteudo.setCorpo(bstring);
            conteudo.setTipo(Files.probeContentType(file_path));
            conteudo.setNome((file_path.getFileName()).toString());

            pacote.setConteudo(conteudo);

            //Obtendo Mensagem
            MensagemProto.Mensagem msg = pacote.build();
            byte[] buffer = msg.toByteArray();
            String novoDestino = ""; //armazena destinatario

            if((this.grupo).equals("") == true){//se nao for para grupo
                (this.channel).basicPublish("", this.destination + "F", null, buffer);
                novoDestino = "@" + this.destination;
            }
            else {
                (this.channel).basicPublish(this.grupo+"F", "", null, buffer);
                novoDestino = "#" + this.destination;
            }

            System.out.println("\nArquivo \"" + this.nome_arquivo + "\" foi enviado para " + novoDestino + " !");
            System.out.print(Chat.user_Destination + ">>");

        }catch(Exception e){
            System.out.println("ERRO NO ENVIO!");
        }
    }
}