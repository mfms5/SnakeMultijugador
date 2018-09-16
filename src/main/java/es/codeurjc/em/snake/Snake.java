package es.codeurjc.em.snake;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class Snake {

	private static final int DEFAULT_LENGTH = 5;

	private final int id; //identificador

	private Location head; //donde esta la cabeza
	private final Deque<Location> tail = new ArrayDeque<>(); //resto del cuerpo
	private int length = DEFAULT_LENGTH;

	private final String hexColor;
	private Direction direction; //en que direccion se mueve
	private String name;
	private SnakeGame sala;
	private AtomicInteger puntos = new AtomicInteger();	
	private AtomicInteger numMuertes = new AtomicInteger();
	private AtomicBoolean viva = new AtomicBoolean(true);

	private final WebSocketSession session;

	//Crear serpiente, darle id y jugador (sesion)
	public Snake(int id, WebSocketSession session) {
		this.id = id;
		this.session = session;
		this.hexColor = SnakeUtils.getRandomHexColor();
		resetState();
	}

	private void resetState() {
		this.direction = Direction.NONE;
		this.head = SnakeUtils.getRandomLocation();
		this.tail.clear();
		this.length = DEFAULT_LENGTH;
	}

	//La serpiente se choca
	private synchronized void kill() throws Exception {
		numMuertes.incrementAndGet();
		if(numMuertes.get() <3) {
			resetState();
			puntos.addAndGet(-3);
			sendMessage("{\"type\": \"dead\"}"); //creamos msj como texto para simplificar
			String msg = String.format("{\"type\": \"info\", \"texto\":\"%s\"}", "La serpiente "+name+" se ha chocado "+numMuertes.get()+" veces.");
			sala.broadcast(msg);
		}
		else { //Si es la 3a vez que choca, eliminar de la partida (puede observar, no se elimina del servidor)
			//Hay que eliminar la serpiente de la sala y del mapa de vivas, ningun jugador puede verla
			viva.set(false);
			sala.snakesVivas.remove(this.getId());
			sala.serpMuertas.incrementAndGet();
			String msg = String.format("{\"type\": \"leave\", \"id\":\"%d\", \"puntuacion\":\"%d\", \"nombre\":\"%s\", \"nSnakes\":\"%d\"}", 
					this.getId(), puntos.get(), name, (sala.getNumSnakes() - sala.serpMuertas.get()));
			sala.broadcast(msg);
			//Ademas se mete su puntuacion en el mapa de la sala
			sala.addPuntuacion(id, puntos.get());
			String m = String.format("{\"type\": \"info\", \"texto\":\"%s\"}", "La serpiente "+name+" ha muerto.");
			sala.broadcast(m);
			System.out.println("La serpiente "+this.name+" ha muerto");
		}
		
	}

	//Recompensa: incrementa tamaño y manda msj kill porque ha matado a otra
	private synchronized void reward(int p) throws Exception {
		this.length++;
		this.puntos.addAndGet(p);	
		System.out.println(this.name+" puntos "+this.puntos.get());
	}

	protected synchronized void sendMessage(String msg) throws Exception {
		this.session.sendMessage(new TextMessage(msg));
	}

	//Actualizar serpiente con todas las otras que hay
	public synchronized void update(ConcurrentHashMap<Integer, Snake> snakes) throws Exception {

		Location nextLocation = this.head.getAdjacentLocation(this.direction); //Obtiene direccion siguiente

		//Si salimos del tablero, entramos por el otro lado
		if (nextLocation.x >= Location.PLAYFIELD_WIDTH) {
			nextLocation.x = 0;
		}
		if (nextLocation.y >= Location.PLAYFIELD_HEIGHT) {
			nextLocation.y = 0;
		}
		if (nextLocation.x < 0) {
			nextLocation.x = Location.PLAYFIELD_WIDTH;
		}
		if (nextLocation.y < 0) {
			nextLocation.y = Location.PLAYFIELD_HEIGHT;
		}

		if (this.direction != Direction.NONE) {
			this.tail.addFirst(this.head);
			if (this.tail.size() > this.length) {
				this.tail.removeLast();
			}
			this.head = nextLocation;
		}

		handleCollisions(snakes); //mirar si se choca
	}

	//Si esta serpiente esta viva, por cada una de las demas comprueba donde estan las cabezas y los cuerpos
	private void handleCollisions(ConcurrentHashMap<Integer, Snake> snakes) throws Exception {
		if (viva.get()) {
			for (Snake snake : sala.snakesVivas.values()) {
	
				boolean headCollision = this.id != snake.id && snake.getHead().equals(this.head);
	
				boolean tailCollision = snake.getTail().contains(this.head);
				
				
				//Si me choco, me muero y la otra es recompensada
				if (headCollision || tailCollision) {
					kill();
					if (this.id != snake.id) {
						snake.reward(5);
					}
				} //comprobar colision con comida
				else {
					//Recorrer comida de la sala y comprobar si su posicion equivale a la cabeza de esta serpiente
					Collection<Comida> comidas = sala.getComidas();
					for(Comida comida: comidas) {
						if(comida.getX()==this.head.x && comida.getY()==this.head.y) {
							//Obtener recompensa (aumenta longitud y puntos)
							this.reward(10);
							comidas.remove(comida);
							//Enviar msj por la sala para que se elimine esta comida
							String msg = String.format("{\"type\": \"borrarComida\", \"id\":\"%d\"}", comida.getId());
							sala.broadcast(msg);
							msg = String.format("{\"type\": \"info\", \"texto\":\"%s\"}", "La serpiente "+name+" ha conseguido comida.");
							sala.broadcast(msg);
						}					
					}				
				}
			}
		}
	}

	public synchronized Location getHead() {
		return this.head;
	}

	public synchronized Collection<Location> getTail() {
		return this.tail;
	}

	public synchronized void setDirection(Direction direction) {
		this.direction = direction;
	}

	public int getId() {
		return this.id;
	}

	public String getHexColor() {
		return this.hexColor;
	}
	
	public synchronized void setName (String n) {
		this.name = n;
	}
	
	public synchronized String getName () {
		return this.name;
	}
	
	public synchronized void setSala (SnakeGame n) {
		this.sala = n;
	}
	
	public synchronized SnakeGame getSala () {
		return this.sala;
	}
	
	public int getPuntos() {
		return puntos.get();
	}
	
}
