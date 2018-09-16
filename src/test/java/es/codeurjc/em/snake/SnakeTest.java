package es.codeurjc.em.snake;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.DeploymentException;

import org.junit.BeforeClass;
import org.junit.Test;

public class SnakeTest {

	@BeforeClass
	public static void startServer(){
		Application.main(new String[]{ "--server.port=9000" });
	}
		
	
	/*
	@Test
	public void testConnection() throws Exception {
		
		WebSocketClient wsc = new WebSocketClient();
		wsc.connect("ws://127.0.0.1:9000/snake");
        wsc.disconnect();		
	}*/
	
	
	//El mensaje de join se envia no justo despues de una conexion, sino cuando se ha añadido un jugador a una sala correctamente	
	/*@Test	
	public void testJoin() throws Exception {
		Semaphore sem = new Semaphore(0);
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		
		WebSocketClient wsc = new WebSocketClient();
		wsc.onMessage((session, msg) -> {
			System.out.println("TestMessage: "+msg);
			if(msg.contains("join")) {
				firstMsg.set(msg); 
				sem.release(); //libera el semaforo cuando recibe el join
			} 
		});
		
		//Se conecta al websocket
        wsc.connect("ws://127.0.0.1:9000/snake");        
        System.out.println("Connected");
		
        //Intenta unirse a la sala 1. Si lo consigue, recibira mensaje de join del host
        wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+(int)(Math.random()*100)+"\", \"sala\":\""+1+"\"}}");
		System.out.println("Enviado join");
        
        sem.acquire(); //Se bloquea hasta que llega el mensaje de join
        wsc.disconnect();
        //Si el cliente recibe join es que se le ha agregado a una sala correctamente y se ha generado una serpiente para el
        String msg = firstMsg.get();      
		System.out.println("MENSAJE: "+msg);        
		assertTrue("The fist message should contain 'join', but it is "+msg, msg.contains("join"));
	} */
	
	//El juego comienza automaticamente cuando llega el cuarto jugador a una sala
	//El host indica que el juego comienza enviando un mensaje "startGame"
	/*@Test
	public void testInicioAutomatico () throws Exception {		
		ExecutorService executor = Executors.newFixedThreadPool(4); //Un hilo por jugador
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		Semaphore semMsj = new Semaphore(0);
		Semaphore semDisc = new Semaphore(0);
		
		Runnable tarea = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				if(msg.contains("startGame")) {
					firstMsg.set(msg);
					semMsj.release(); //libera el semaforo cuando llega el msj esperado
				}				
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");			
	        
			//Enviar mensaje JOIN:GAME con el nombre del jugador (numero aleatorio) y de la sala (siempre sala 1)
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+(int)(Math.random()*100)+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}
			
			/*
			try {			
				semDisc.acquire(); //Se bloquea hasta que se ha comprobado el mensaje
				wsc.disconnect();			
				} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
		/*};
		
		for(int i=0; i<4; i++) {
			executor.execute(tarea);
			//Thread.sleep(500);
		}		
		
		//Cuando se reciben 4 joins, el juego deberia comenzar automaticamente
		//El cliente deberia recibir un mensaje de startGame ("{\"type\": \"startGame\"}")
		semMsj.acquire(); //Se bloquea hasta que se ha recibido el msj
		String msg = firstMsg.get();  
		System.out.println("MENSAJE: "+msg);
		assertTrue("The fist message should contain 'startGame', but it is "+msg, msg.contains("startGame"));
		//semDisc.release(4); //Libera 4 permisos para que los 4 hilos puedan desconectar el socket	
		//executor.shutdown();
	}*/
	
	//El juego puede iniciarse con dos jugadores si el creador lo solicita
	//En la aplicacion real, cuando lleg un segundo jugador se muestra un boton en la pagina del jugador que ha creado la salla
	//Al pulsar el boton, el cliente envia un msj al host para que comience el juego
	//Cuando el host recibe dicho mensaje, notifica a todos los jugadores que ha empezado el juego con un msj "startGame"
	@Test
	public void testInicioConDos () throws Exception {
		AtomicReference<String> firstMsg = new AtomicReference<String>();	
		Semaphore msj = new Semaphore(0);
		
		Runnable hilo1 = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				//El mensaje showButtonStart se envia cuando ya hay 2 jugadores y el creador puede iniciar la partida
				if(msg.contains("showButtonStart")) {
					//Cuando llega este mensaje, "se pulsa el boton" y el cliente envia un mensaje STARTED para indicar que ha iniciado su partida
					try {
						wsc.sendMessage("{\"action\": \"STARTED\"}");
						System.out.println("Enviado started");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
				        
			//Crea la sala 1 y se une a ella
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+(int)(Math.random()*100)+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}			
		};
		
		Runnable hilo2 = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				//Cuando la partida es iniciada por el creador, el host manda un mensaje startGame al otro jugador 
				//El creador ya habria iniciado la partida por si mismo. El otro jugador la inicia al recibir este msj
				if(msg.contains("startGame")) {
					firstMsg.set(msg);
					msj.release(); //Libera semaforo cuando se recibe el msj esperado
				}
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
			
	        
			//Se une a la sala creada por el otro hilo
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+(int)(Math.random()*100)+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}				
		};
		
		Thread h1 = new Thread(hilo1); //iniciar hilo jugador creador
		h1.start();
		Thread.sleep(500);
		Thread h2 = new Thread(hilo2); //iniciar hilo segundo jugador
		h2.start();
		
		msj.acquire(); //Se bloquea hasta recibir el msj de start
		//El segundo jugador deberia recibir un mensaje de startGame ("{\"type\": \"startGame\"}")
		String msg = firstMsg.get();  
		System.out.println("MENSAJE: "+msg);
		assertTrue("The fist message should contain 'startGame', but it is "+msg, msg.contains("startGame"));
	} 
	
	//El juego finaliza cuando solo queda un jugador
	//Se crean dos jugadores/hilos. El creador inicia partida cuando llega el segundo jugador
	//Se supone que el creador se choca tres veces y muere, por lo que solo quedaria el jugador 2
	//En la aplicacion real, el cliente notifica al host que solo queda un jugador cuando recibe un mensaje indicando que una serpiente ha muerto
	//En el test, como no se ejecuta la parte javascript, el jugador 2 debe enviar al host un mensaje FINPARTIDA para indicar que es el ultimo que queda
	//Cuando el host recibe FINPARTIDA, envia a todos los clientes un msj finPartida
	//Cuando los clientes reciben finPartida, se mostrarian las puntuaciones y se termina el juego
	/*@Test
	public void testFinalizar () throws Exception {
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		Semaphore msj = new Semaphore(0);
		
		//Hilo creador
		Runnable hilo1 = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				//El mensaje showButtonStart se recibe en el cliente cuando ya hay 2 jugadores y el creador puede iniciar la partida
				if(msg.contains("showButtonStart")) {
					//Cuando llega este mensaje, "se pulsa el boton" y el cliente envia un mensaje STARTED para indicar que ha iniciado su partida
					try {
						wsc.sendMessage("{\"action\": \"STARTED\"}");
						System.out.println("Enviado started");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				//Host envia finPartida cuando un jugador le indica que solo queda el, indica que se ha finalizado el juego
				} else if (msg.contains("finPartida")) {
					firstMsg.set(msg);
					msj.release();
				}
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
				        
			//Crea la sala 1 y se une a ella
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+(int)(Math.random()*100)+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}			
		};
		
		//Hilo jugador2
		Runnable hilo2 = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				//Host envia finPartida cuando un jugador le indica que solo queda el, indica que se ha finalizado el juego
				if (msg.contains("finPartida")) {
					firstMsg.set(msg);
					msj.release();
				}
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
			
	        
			//Se une a la sala creada por el anterior jugador
			try {
				int nombre = (int)(Math.random()*100);
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+nombre+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
				Thread.sleep(500);
				//Envia un mensaje FINPARTIDA para indicar que es el ultimo que quedaba y que debe finalizarse el juego
				wsc.sendMessage("{\"action\": \"FINPARTIDA\", \"data\": {\"nombre\":\""+nombre+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado FINPARTIDA");
				
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}			
		};
		
		Thread h1 = new Thread(hilo1);
		h1.start();//iniciar hilo jugador creador
		Thread.sleep(500); //esperar
		Thread h2 = new Thread(hilo2);
		h2.start();//iniciar hilo segundo jugador
		
		msj.acquire(); //Se bloquea hasta que el msj finPartida llega a alguno de los hilos
		String msg = firstMsg.get();  
		System.out.println("MENSAJE: "+msg);
		//Cuando el jugador 2 indica fin partida, todos los clientes deberian recibir un msj "finPartida"
		assertTrue("The fist message should contain 'finPartida', but it is "+msg, msg.contains("finPartida"));
		h1.interrupt();
		h2.interrupt();
	}*/
	
	//Si un quinto jugador quiere entrar en una sala, no entrará hasta que otro finalice, 
	//momento en el que entrará de forma automática.
	/*@Test
	public void testInicioAutomatico () throws Exception {		
		ExecutorService executor = Executors.newFixedThreadPool(3); 
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		Semaphore msj = new Semaphore(0);
		Semaphore leave = new Semaphore(0);
		
		//Jugadores genericos que simplemente entran a la sala
		Runnable tarea = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
				        
			//Enviar mensaje JOIN:GAME con el nombre del jugador (numero aleatorio) y de la sala (siempre sala 1)
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+(int)(Math.random()*100)+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}						
		};
		
		//Cuarto jugador que entra a la sala y al rato se va
		//Para irse de la sala hay que cerrar la conexion con el ws
		Runnable cuarto = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos"))  System.out.println("TestMessage: "+msg);
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
				        
			//Enviar mensaje JOIN:GAME con el nombre del jugador (numero aleatorio) y de la sala (siempre sala 1)
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+4+"\", \"sala\":\""+1+"\"}}");
				System.out.println("Enviado join");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}
			
			try {
				leave.acquire(); //Espera a que el quinto jugador haya intentado join antes de irse
				System.out.println("El cuarto jugador sale de la sala");
				wsc.disconnect();
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		};

		//Quinto jugador, intenta entrar pero se queda bloqueado hasta que el cuarto se va
		//Cuando se desbloquea, recibe un join --> cuando se recibe join es que el test es correcto
		Runnable quinto = () -> {
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				if(msg.contains("join")) {
					firstMsg.set(msg);
					msj.release(); //libera el semaforo cuando consigue unirse a la sala
				}
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println("Connected");
				        
			//Enviar mensaje JOIN:GAME con el nombre del jugador (numero aleatorio) y de la sala (siempre sala 1)
			try {
				wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+5+"\", \"sala\":\""+1+"\"}}");
				System.out.println("El quinto jugador envia join");
				leave.release(); //Libera el semaforo para que el cuarto jugador pueda irse
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}
			
		};
		
		//Crear hilos genericos
		for(int i=0; i<3; i++) {
			executor.execute(tarea);
			Thread.sleep(500);
		}
		//Crear cuarto jugador
		Thread j4 = new Thread(cuarto);
		j4.start();
		Thread.sleep(1000); //Esperar antes de crear el quinto
		Thread j5 = new Thread(quinto);
		j5.start();
		
		//Cuando el quinto jugador recibe join es que se ha unido a la sala al salir el cuarto
		msj.acquire(); //Se bloquea hasta que el join llega al quinto jugador
		String msg = firstMsg.get();  
		System.out.println("MENSAJE: "+msg);
		assertTrue("The fist message should contain 'join', but it is "+msg, msg.contains("join"));
		
		//Interrumpir hilos
		j4.interrupt();
		j5.interrupt();
		executor.shutdownNow();
	}*/

	
	/*@Test
	public void testCarga () throws Exception {		
		ExecutorService executor = Executors.newFixedThreadPool(3); 
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		AtomicInteger numJugador = new AtomicInteger();
		CountDownLatch finSalaA = new CountDownLatch(4); //Permisos para terminar la partida en salaA
		CountDownLatch finSalaB = new CountDownLatch(4); ////Permisos para terminar la partida en salaB
		CountDownLatch dirSalaA = new CountDownLatch(1); //Permisos para enviar mensajes de direccion en salaA
		CountDownLatch dirSalaB = new CountDownLatch(1); //Permisos para enviar mensajes de direccion en salaB
		CountDownLatch msj = new CountDownLatch(2); //Esperar al mensaje
	
		Runnable tarea = () -> {
			int n = numJugador.incrementAndGet();
			
			WebSocketClient wsc = new WebSocketClient();
			
			wsc.onMessage((session, msg) -> {
				if(!msg.contains("update") && !msg.contains("puntos")) System.out.println("TestMessage: "+msg);
				//La partida ha finalizado correctamente
				if(msg.contains("finPartida")) {
					firstMsg.set(msg); //Mensaje que llega cuando todo finaliza correctamente
				} else if(msg.contains("startGame") && n<6) {
					//dirSalaA.countDown();
					finSalaA.countDown(); //Una vez ha empezado el juego, se da permiso a los hilos para finalizar su partida
				}
				else if (msg.contains("startGame") && n>=6) {
					//dirSalaA.countDown();
					finSalaB.countDown(); //Una vez ha empezado el juego, se da permiso a los hilos para finalizar su partida
				}
			});
			
			try {
				wsc.connect("ws://127.0.0.1:9000/snake");
			} catch (DeploymentException | IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.out.println("error al conectar con ws");
			}	        
	        System.out.println(n+ " Connected");
			
	        //Enviar mensaje JOIN:GAME con el nombre del jugador y sala segun el numero de jugador
			try {
				switch(n) {
				//El primer jugador crea la salaA y entra. 
				case 1:
					wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+n+"\", \"sala\":\""+"salaA"+"\"}}");
					break;
				//Los tres siguientes esperan 1seg, entran y comienza el juego. El quinto no entra porque ya hay 4
				case 2: 
				case 3: 
				case 4:
				case 5: 
					Thread.sleep(1000);
					wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+n+"\", \"sala\":\""+"salaA"+"\"}}");
					System.out.println("El jugador "+n+" ha enviado join");
					break;
				//El sexto jugador crea salaB y entra. 
				case 6:
					wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+n+"\", \"sala\":\""+"salaB"+"\"}}");
					break;
				//Los tres siguientes esperan 1seg, entran y comienza el juego. El decimo no entra porque ya hay 4.
				case 7:
				case 8: 
				case 9: 
				case 10:
					Thread.sleep(1000);
					wsc.sendMessage("{\"action\": \"JOIN_GAME\", \"data\": {\"nombre\":\""+n+"\", \"sala\":\""+"salaB"+"\"}}");
					System.out.println("El jugador "+n+" ha enviado join");
					break;
				}						
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}*/
			
			//Aqui se deberia simular el juego para aumentar/disminuir puntuaciones, pero no se puede porque 
			// las recompensas y penalizaciones las gestiona el servidor al detectar colisiones
			//Desde el cliente solo se puede enviar la direccion en la que se mueve la serpiente			
			/*try {
				//Deberian esperar a recibir un startGame antes de enviar direcciones, pero se bloquean indefinidamente a partir del 3er jugador
				//Errores al enviar mensajes a serpientes que ya no existen porque ha finalizado la partida
				/*
				if(n<6) {					
					dirSalaA.await();
				}
				else dirSalaB.await();*/
				
				/*if((n%2)==0) {
					if(n<6) {
						wsc.sendMessage("{\"action\": \"DIRECTION\", \"data\": {\"data\":\""+"east"+"\"}}");
						//finSalaA.countDown();
					}
					else {
						wsc.sendMessage("{\"action\": \"DIRECTION\", \"data\": {\"data\":\""+"south"+"\"}}");
						//finSalaB.countDown();
					}
				} else {
					if(n<6)	{
						wsc.sendMessage("{\"action\": \"DIRECTION\", \"data\": {\"data\":\""+"west"+"\"}}");
						//finSalaA.countDown();
					}
					else {
						wsc.sendMessage("{\"action\": \"DIRECTION\", \"data\": {\"data\":\""+"north"+"\"}}");
						//finSalaA.countDown();
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}*/
			
			//Esperar 10 segundos y salir de la sala. 
			//Cuando solo quedan los jugadores 1 y 6 en sus respectivas salas, se muestran las puntuaciones totales		
			//Como en el testFinalizar(), es el cliente quien notifica al host que solo queda un jugador cuando recibe un mensaje indicando que una serpiente ha muerto
			//Como no se puede simular el juego y no se ejecuta la parte javascript, los jugadores 1 y 6 envian el mensaje FINPARTIDA 
			// sin esperar realmente a recibir el aviso de abandono desde el host
			/*try {						
				if(n==1) {
					Thread.sleep(10000);
					finSalaA.await();
					//Envia un mensaje FINPARTIDA para indicar que es el ultimo que quedaba y que debe finalizarse el juego
					wsc.sendMessage("{\"action\": \"FINPARTIDA\", \"data\": {\"nombre\":\""+n+"\", \"sala\":\""+"salaA"+"\"}}");
					System.out.println("Enviado FINPARTIDA en salaA");
					msj.countDown(); //Libera 1 para indicar fin de partida en salaA
				} else if(n==6) {
					Thread.sleep(10000);
					finSalaB.await();
					//Envia un mensaje FINPARTIDA para indicar que es el ultimo que quedaba y que debe finalizarse el juego
					wsc.sendMessage("{\"action\": \"FINPARTIDA\", \"data\": {\"nombre\":\""+n+"\", \"sala\":\""+"salaB"+"\"}}");
					System.out.println("Enviado FINPARTIDA en salaB");
					msj.countDown(); //Libera 1 para indicar fin de partida en salaB
				}
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Error al enviar mensaje");
			}
		}; 
		
		//Crear hilos 
		for(int i=0; i<10; i++) {
			executor.execute(tarea);
			Thread.sleep(1000);
		}
		
		//Deberia recibirse un mensaje de fin partida. Las puntuaciones deberian mostrarse en la consola
		msj.await(); //Espera a que las dos salas notifiquen el fin de la partida
		String msg = firstMsg.get();  
		System.out.println("MENSAJE: "+msg);
		assertTrue("The fist message should contain 'finPartida', but it is "+msg, msg.contains("finPartida"));
		
		//Interrumpir hilos
		executor.shutdownNow();
	}*/
		
}
