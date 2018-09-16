package es.codeurjc.em.snake;

import java.util.concurrent.atomic.AtomicInteger;

public class Comida {

	private AtomicInteger id = new AtomicInteger();	

	private AtomicInteger x = new AtomicInteger();
	private AtomicInteger y = new AtomicInteger();
	
	public Comida (AtomicInteger i, AtomicInteger x, AtomicInteger y) {
		id = i;
		this.x = x;
		this.y = y;
	}
	
	public int getId() {
		return id.get();
	}

	public void setId(AtomicInteger id) {
		this.id = id;
	}

	public int getX() {
		return x.get();
	}

	public void setX(AtomicInteger x) {
		this.x = x;
	}

	public int getY() {
		return y.get();
	}

	public void setY(AtomicInteger y) {
		this.y = y;
	}
}
