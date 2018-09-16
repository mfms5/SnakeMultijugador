//Gestor de msjs
package es.codeurjc.em.snake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnakeHandler extends TextWebSocketHandler {

	private static final String SNAKE_ATT = "snake";

	private AtomicInteger snakeIds = new AtomicInteger(0);

	
	//Mapa con las salas, cada sala es un SnakeGame, la clave es el nombre
	private ConcurrentHashMap<String, SnakeGame> salas = new ConcurrentHashMap<>();
	//Lista de puntuaciones de todas las partidas/salas
	List<String[]> puntuacionesGlobales = Collections.synchronizedList(new ArrayList<>());
	
	enum ClientToServerAction {
		JOIN_GAME, DIRECTION, PING, STARTED, INTERRUMPIR, FINPARTIDA
	}
	
	static class ClientToServerMsg {
		ClientToServerAction action;
		Data data;
	}
	
	static class Data {
		String nombre;
		String sala;
		String data;
	}
	
	private ObjectMapper json = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY);


	@Override
	//Cuando se crea conexion, se crea serpiente, se añade etc
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {

		System.out.println("Conexion establecida");
		
	}

	/* (non-Javadoc)
	 * @see org.springframework.web.socket.handler.AbstractWebSocketHandler#handleTextMessage(org.springframework.web.socket.WebSocketSession, org.springframework.web.socket.TextMessage)
	 */
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		try {

			String jsonMsg = message.getPayload();
			ClientToServerMsg msg; //info del msj que ha llegado
			
			msg = json.readValue(jsonMsg, ClientToServerMsg.class);	
			
			//Acciones que pueden llegar del cliente
			switch (msg.action) {
			case PING:		
				return;
				
			case JOIN_GAME:			
				System.out.println("Recibido: "+jsonMsg);
				//Crear nueva serpiente con su correspondiente id y nombre
				int id = snakeIds.getAndIncrement();
				Snake s = new Snake(id, session);				
				s.setName(msg.data.nombre);
			
				//Comprobar si la sala ya existe, si hay hueco o si hay que crearla
				String nombreSala = msg.data.sala;
				SnakeGame salaAux = salas.get(nombreSala); //Obtener sala cuya clave es el nombre enviado
				
				//Si la sala no existe, la crea
				if(salaAux == null) {
					salas.put(nombreSala, new SnakeGame()); //meter nueva sala en el mapa
					salaAux = salas.get(nombreSala); //recuperar la que se acaba de meter
					salaAux.setName(nombreSala); //ponerle el nombre
					salaAux.setIdCreador(id);
					salaAux.addSnake(s);
					s.setSala(salaAux);
				}
				else { //Si la sala existe, intenta meter a la serpiente
					if(salaAux.addSnake(s)) { //Si devuelve true, es que la ha metido en la sala
						s.setSala(salaAux); //indicar sala a la que pertenece la serpiente	
					}
					else { //Si devuelve false es que ya hay 4 jugadores y ninguno ha salido en el tiempo de espera
						System.out.println("Ya hay 4 jugadores en esta sala");
						String failedJoin = String.format("{\"type\": \"failedJoin\"}");					
						//Enviar msj solo al jugador/serpiente que ha intentado unirse
						session.sendMessage(new TextMessage(failedJoin));
						session.close(); //cerrar la sesion
						return;
					}
				}
				
				//Si funciona, ponerla en la sesion
				session.getAttributes().put(SNAKE_ATT, s);
				System.out.println("Jugador "+s.getName()+" anadido correctamente a la sala "+salaAux.getName());
				sendMessage(salaAux, msg.data.nombre); //envia msj de join
				
				//Si es el 4o jugador de la sala y no se ha pulsado el boton de empezar, el juego comienza automaticamente
				if(salaAux.getNumSnakes()==4 && !salaAux.getStarted()) {
					salaAux.empezarJuego(); 
				}
				else if(salaAux.getStarted()) { //Si no es el 4o pero la partida ya ha empezado, empieza automaticamente
					String m = String.format("{\"type\": \"startGame\"}");
					s.sendMessage(m);
				}					
				
				//Actualizar hall para mostrarlo			
				actualizarHallFama();
				
				break;
				
			case DIRECTION:
				System.out.println("Recibido: "+jsonMsg);
				Snake sn = (Snake) session.getAttributes().get(SNAKE_ATT);
				Direction d = Direction.valueOf((msg.data.data).toUpperCase());
				sn.setDirection(d);
				break;
				
			case STARTED:
				//Todos los jugadores que ya se habian unido a la sala deben iniciar el juego
				//excepto el creador (que ha pulsado el boton y ya ha iniciado el juego)
				String m = String.format("{\"type\": \"startGame\"}");
				s = (Snake) session.getAttributes().get(SNAKE_ATT); //jugador creador de la sala
				SnakeGame sala = s.getSala(); //obtener nombre de la sala
				sala.setStarted(true);
				for (Snake serp : sala.getSnakes()) {
					if (serp.getId() != sala.getIdCreador()) {
						try {
							serp.sendMessage(m);

						} catch (Throwable ex) {
							System.err.println("Execption sending message to snake " + serp.getId());
							ex.printStackTrace(System.err);
						}
						
					}			
				}
				//Ademas hay que iniciar las comidas
				sala.iniciarComidas();
				break;
			
			case FINPARTIDA:
				System.out.println("Fin partida recibido");
				(salas.get(msg.data.sala)).finalizar(msg.data.nombre);
				break;
				
			}			

		} catch (Exception e) {
			System.err.println("Exception processing message " + message.getPayload());
			e.printStackTrace(System.err);
		}
	}

	@Override
	//Cerrar conexion, se elimina serpiente
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

		System.out.println("Connection closed. Session " + session.getId());

		Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
				
		//Obtener la sala en la que estaba la serpiente y eliminarla de ella
		if (s!=null) {
			SnakeGame sala = s.getSala();
			sala.removeSnake(s);			
	
			String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
			System.out.println("Se ha eliminado la serpiente "+s.getName()+" de la sala "+sala.getName());
			//Si era el ultimo jugador que quedaba, eliminar la sala
			if(sala.getNumSnakes()==0) {
				System.out.println("Se ha eliminado la sala "+sala.getName());
				salas.remove(sala);
			} else { //Si aun quedan jugadores, hace broadcast
				sala.broadcast(msg);
			}
		}
		
	}
	
	public void sendMessage (SnakeGame sala, String nombre) throws Exception {
		StringBuilder sb = new StringBuilder();
		
		for (Snake snake : sala.getSnakes()) {			
			sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
			sb.append(',');
		}
		sb.deleteCharAt(sb.length()-1);
		String msg = String.format("{\"type\": \"join\", \"data\":[%s], \"snake\":\"%s\"}", sb.toString(), nombre);
		sala.broadcast(msg);
	}
	
	public void actualizarHallFama () throws Exception {
		for (Entry<String, SnakeGame> sala : salas.entrySet()) {
			puntuacionesGlobales.addAll((sala.getValue()).getLista());
		}
		Collections.sort(puntuacionesGlobales,(o1,o2)-> Integer.parseInt(o2[1])-Integer.parseInt(o1[1]));
		
		//Generar msj con las 10 primeras puntuaciones
		StringBuilder sb = new StringBuilder();	
		int i=0;
		while (i<10 && i<puntuacionesGlobales.size()) {	
			String[] aux = puntuacionesGlobales.get(i);
			sb.append(String.format("{\"nombre\": \"%s\", \"puntos\": \"%s\"}", aux[0], aux[1]));
			sb.append(',');
			i++;			
		}
		if(sb.length()>0) sb.deleteCharAt(sb.length()-1);
		String msg = String.format("{\"type\": \"hallFama\", \"data\":[%s]}", sb.toString());
		//Enviarlo a todas las salas
		for(Entry<String, SnakeGame> sala : salas.entrySet()) {
			(sala.getValue()).broadcast(msg);
		}
	}

}
