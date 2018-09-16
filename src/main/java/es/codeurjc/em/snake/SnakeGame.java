//Equivalente al board -- SALAS
package es.codeurjc.em.snake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class SnakeGame {

	private final static long TICK_DELAY = 100; //cada 100ms envia status del juego

	private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>(); //tengo todas las snakes de la sala
	public ConcurrentHashMap<Integer, Snake> snakesVivas = new ConcurrentHashMap<>(); //solo serpientes vivas
	private AtomicInteger numSnakes = new AtomicInteger();
	private String nombreSala;
	private int idCreador;
	private Semaphore huecos = new Semaphore(4);
	private boolean dentro;
	private AtomicBoolean started = new AtomicBoolean(false);
	public ConcurrentHashMap<AtomicInteger, Comida> comidas = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, Integer> puntuaciones = new ConcurrentHashMap<>(); //mapa con puntuaciones de las serpientes
	public AtomicInteger serpMuertas = new AtomicInteger();
	private	List<String[]> lista = new ArrayList<String[]>();
	private Lock lockLista = new ReentrantLock();

	private ScheduledExecutorService scheduler; //scheduler para enviar cosas cada 100 ms
	private ScheduledExecutorService schComidas; //Scheduler para generar comidas cada 20s

	public boolean  addSnake(Snake snake) throws Exception {
			
		try {
			dentro = huecos.tryAcquire(5L, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Espera interrumpida");
		}
		
		if(dentro) {
			snakes.putIfAbsent(snake.getId(), snake);
			snakesVivas.putIfAbsent(snake.getId(), snake);
			int count = numSnakes.getAndIncrement();
			
			//Si soy la primera, arranco el temporizador (atomic integer!)
			if (count == 0) {
				startTimer();
			}
			//Si es la segunda, manda mensaje a sesion creador para que muestre el boton de empezar
			if(count == 1) {
				for (Snake s : getSnakes()) {
					if (s.getId() == idCreador) {
						try {
							String msg = String.format("{\"type\": \"showButtonStart\"}");
							s.sendMessage(msg);

						} catch (Throwable ex) {
							System.err.println("Exception sending message to snake " + s.getId());
							ex.printStackTrace(System.err);
							removeSnake(s);
						}
						
					}			
				}
				
			}
		}
		
		return dentro;
	}

	public Collection<Snake> getSnakes() {
		return snakes.values();
	}
	
	public Collection<Snake> getSnakesVivas() {
		return snakesVivas.values();
	}

	public void removeSnake(Snake snake) throws Exception {

		snakes.remove(Integer.valueOf(snake.getId()));

		int count = numSnakes.decrementAndGet();
		huecos.release();

		if (count == 0) {
			stopTimer();
		} 
	}

	
	private void tick() {

		try {

			//Actualizar snakes que se han movido
			for (Snake snake : getSnakes()) {
				snake.update(snakesVivas);
			}

			StringBuilder sb = new StringBuilder();
			StringBuilder sbp = new StringBuilder();
			for (Snake snake : getSnakes()) {
				sb.append(getLocationsJson(snake));
				sb.append(',');
				
				sbp.append(String.format("{\"nombre\": \"%s\", \"puntos\": \"%s\", \"color\": \"%s\"}", snake.getName(), snake.getPuntos(), snake.getHexColor()));
				sbp.append(',');
			}
			sb.deleteCharAt(sb.length()-1);
			sbp.deleteCharAt(sbp.length()-1);
			String msg = String.format("{\"type\": \"update\", \"data\" : [%s]}", sb.toString());
			broadcast(msg); //por cada serpiente envia el msj
			//mensaje de puntuaciones
			String msgp = String.format("{\"type\": \"puntos\", \"data\" : [%s]}", sbp.toString());
			broadcast(msgp); 

		} catch (Throwable ex) {
			System.err.println("Exception processing tick()");
			ex.printStackTrace(System.err);
		}
	}

	private String getLocationsJson(Snake snake) {

		synchronized (snake) {

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("{\"x\": %d, \"y\": %d}", snake.getHead().x, snake.getHead().y));
			for (Location location : snake.getTail()) {
				sb.append(",");
				sb.append(String.format("{\"x\": %d, \"y\": %d}", location.x, location.y));
			}

			return String.format("{\"id\":%d,\"body\":[%s]}", snake.getId(), sb.toString());
		}
	}

	public void broadcast(String message) throws Exception {

		for (Snake snake : getSnakes()) {
			try {

				//System.out.println("Sending message " + message + " to " + snake.getId());
				snake.sendMessage(message);

			} catch (Throwable ex) {
				System.err.println("Execption sending message to snake " + snake.getId());
				ex.printStackTrace(System.err);
				removeSnake(snake);
			}
		}
	}

	//Ejecutar metodo tic cada 100ms
	public void startTimer() {
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY, TICK_DELAY, TimeUnit.MILLISECONDS);
	}

	public void stopTimer() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
	}
	
	public synchronized String getName () {
		return nombreSala;
	}
	
	public synchronized void setName (String n) {
		this.nombreSala = n;
	}
	
	public synchronized int getIdCreador () {
		return idCreador;
	}
	
	public synchronized void setIdCreador (int n) {
		this.idCreador = n;
	}
	
	public int getNumSnakes () {
		return numSnakes.get();
	}
	
	public boolean getStarted() {
		return started.get();
	}
	
	public void setStarted (boolean s) {
		started.set(s);
	}
	
	
	//Manda mensaje a todos para que empiece el juego
	public void empezarJuego () throws Exception {		
		System.out.println("Empezar automaticamente");
		String msg = String.format("{\"type\": \"startGame\"}");
		System.out.println("Host envia "+msg);
		broadcast(msg); //broadcast porque este metodo lo llama un solo jugador y el msj tienen que recibirlo todos
		//Ademas hay que iniciar las comidas
		iniciarComidas();
			
	}
	
	//Generar una comida cada 20 segundos
	public void iniciarComidas () {
		schComidas = Executors.newScheduledThreadPool(1);
		
		Runnable task = new Runnable () {
			public void run () {
				//Obtener datos para la nueva comida y añadirla al array de la sala
				AtomicInteger id = new AtomicInteger (comidas.size());
				Location loc = SnakeUtils.getRandomLocation();
				comidas.putIfAbsent(id, new Comida(id, new AtomicInteger(loc.x), new AtomicInteger(loc.y)));
				
				//Mandar msj a todas las serpientes para que sepan que se ha creado comida nueva
				String msg = String.format("{\"type\": \"comidaCreada\", \"id\":\"%d\", \"x\":\"%d\", \"y\":\"%d\"}", 
						id.get(), loc.x, loc.y);
				try {
					broadcast(msg);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		
		//Iniciar scheduler
		schComidas.scheduleAtFixedRate(task, 2, 20, TimeUnit.SECONDS); //Se crea comida cada 20 segundos		
	}
	
	public Collection<Comida> getComidas() {
		return comidas.values();
	}
	
	public void addPuntuacion(Integer id, Integer puntos) {
		puntuaciones.putIfAbsent(id, puntos);
	}
	
	//Se llama cuando se debe finalizar la partida
	public void finalizar (String nombre) throws Exception {
		stopTimer();
		//Anade las puntuaciones de todas las serpientes si no estaban ya
		for(Snake s: snakes.values()) {
			puntuaciones.putIfAbsent(s.getId(), s.getPuntos());
		}
		escribirPuntuaciones(); //actualizar lista de puntuaciones 
		lockLista.lock();
		Collections.sort(lista,(o1,o2)-> Integer.parseInt(o2[1])-Integer.parseInt(o1[1])); //Ordenar puntuaciones de mayor a menor
		lockLista.unlock();
		
		//Envia un msj al cliente para notificarle el fin de partida y las puntuaciones finales	
		int pMax = 0;
		int idGanador = 0;
		StringBuilder sb = new StringBuilder();
		for(String[] p: lista) {
			sb.append(String.format("{\"nombre\":\"%s\", \"puntos\": %s}", p[0], p[1]));
			sb.append(',');
			
			//Encontrar serpiente ganadora
			if(Integer.parseInt(p[1]) >= pMax) {
				pMax = Integer.parseInt(p[1]);
				idGanador = buscarSerpiente(p[0]).getId();
			}
		}
		sb.deleteCharAt(sb.length()-1);	
		String msg = String.format("{\"type\": \"finPartida\", \"data\":[%s], \"ganador\":\"%s\", \"id\":\"%d\"}", sb.toString(), (snakes.get(idGanador)).getName(), idGanador);		
		broadcast(msg);	
				
		mostrarPuntuaciones(); //mostrar puntuaciones por pantalla
	}
	
	//Metodo auxiliar para buscar una serpiente en el mapa dado su nombre
	private Snake buscarSerpiente (String nombre) {
		for (Entry<Integer, Snake> s : snakes.entrySet()) {	
			if (((s.getValue()).getName()).equals(nombre)) {
				return s.getValue();
			}			
		}
		return null;
	}
	
	//Pasar puntuaciones del mapa a una lista para que sean añadidas a la lista global
	public synchronized void escribirPuntuaciones () {
		for (Entry<Integer, Integer> p : puntuaciones.entrySet()) {				
			//Añadir puntuaciones a lista global
			String[] string = new String[2];
			string[0] = (snakes.get(p.getKey())).getName();
			string[1] = ""+p.getValue();
			lista.add(string);			
		}
	}
	
	public synchronized List<String[]> getLista () {
		return lista;
	}
	
	//Muestra puntuaciones de la sala por pantalla, ordenadas d
	public synchronized void mostrarPuntuaciones () {
		Collections.sort(lista,(o1,o2)-> Integer.parseInt(o2[1])-Integer.parseInt(o1[1]));
		System.out.println("Puntuaciones sala: "+nombreSala);
		for(String[] p: lista) {
			System.out.println(p[0]+": "+p[1]);
		}
	}
}
