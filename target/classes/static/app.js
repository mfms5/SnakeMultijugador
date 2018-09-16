let socket = null;

var Console = {}; //Consola para poner mensajes
var HallFama = {}; //Mostrar hall fama

Console.log = (function(message) {
	var console = document.getElementById('console');
	var p = document.createElement('p');
	p.style.wordWrap = 'break-word';
	p.innerHTML = message;
	console.appendChild(p);
	while (console.childNodes.length > 25) {
		console.removeChild(console.firstChild);
	}
	console.scrollTop = console.scrollHeight;
});

HallFama.log = (function (message) {
    var hall = document.getElementById('hallFame');
    var p = document.createElement('p');
    p.style.wordWrap = 'break-word';
    p.innerHTML = message;
    hall.appendChild(p);
    while (hall.childNodes.length > 25) {
        hall.removeChild(hall.firstChild);
    }
    //console.scrollTop = console.scrollHeight;
});

const events = {

    outgoing: {
        JOIN_GAME: 'JOIN_GAME',
        DIRECTION: 'DIRECTION',
        PING: 'PING',
        STARTED: 'STARTED',
        INTERRUMPIR: 'INTERRUMPIR',
        FINPARTIDA: 'FINPARTIDA'
    }        
}

function sendMessage(action, data) {

    let resp = {
        action: action,
        data: data
    };

    socket.send(JSON.stringify(resp));
}

let game;

//Clase serpiente con posiciones, body, color
class Snake {

	constructor() {
		this.snakeBody = [];
		this.color = null;
	}

	//Decirle que se pinte un cuadrado por cada posicion
	draw(context) {
		for (var pos of this.snakeBody) {
			context.fillStyle = this.color;
			context.fillRect(pos.x, pos.y,
				game.gridSize, game.gridSize);
		}
		
	}
}

var nSnakes = 0;
var viva = true;

//Clase comidas
class Comida {
    constructor(i, px, py) {
        this.id = i;
        this.x = px;
        this.y = py;
    }
    
    //Pintar un cuadrado amarillo en la posicion indicada
    draw(context) {
        context.fillStyle = "#FFFF00";
        context.fillRect(this.x, this.y, game.gridSize, game.gridSize);
    }
}

//Juego

class Game {

	constructor(){
	
		this.fps = 30; //veces por seg que se actualiza
		this.socket = null;
		this.nextFrame = null;
		this.interval = null;
		this.direction = 'none';
		this.gridSize = 10; //tamano inicial
		
		this.skipTicks = 1000 / this.fps; //Ticks del cliente (veces que cliente pinta estado del mundo)
		this.nextGameTick = (new Date).getTime();
	}

	initialize() {	
	
	    this.snakes = [];
	    this.comidas = [];	    
		let canvas = document.getElementById('playground'); //sitio donde se va a pintar rejilla
		if (!canvas.getContext) {
			Console.log('Error: 2d canvas not supported by this browser.');
			return;
		}		
		
		this.context = canvas.getContext('2d');
		//Si alguien pulsa una tecla, se manda el msj correspondiente al servidor
		window.addEventListener('keydown', e => {
			
			var code = e.keyCode;
			if (code > 36 && code < 41) {
				switch (code) {
				case 37:
					if (this.direction != 'east')
					    this.setDirection('west');					
					break;
				case 38:
					if (this.direction != 'south')
						this.setDirection('north');
					break;
				case 39:
					if (this.direction != 'west')
						this.setDirection('east');
					break;
				case 40:
					if (this.direction != 'north')
						this.setDirection('south');
					break;
				}
			}
		}, false);
		
		this.connect();
	}

	setDirection(direction) {
		this.direction = direction;
		sendMessage(events.outgoing.DIRECTION, { nombre: this.nombre, data: direction });
		Console.log('Sent: Direction ' + direction);
	}

	startGameLoop() {
	
		this.nextFrame = () => {
			requestAnimationFrame(() => this.run()); //Se invoca el run cada vez que se actualiza la pantalla
		}
		
		this.nextFrame();	
	}

	stopGameLoop() {
		this.nextFrame = null;
		if (this.interval != null) {
			clearInterval(this.interval);
		}
	}

	//Pinta cada serpiente
	draw() {
	    this.context.clearRect(0, 0, 640, 480);
		for (var id in this.snakes) {			
		    this.snakes[id].draw(this.context);
		}

	    //pintar comidas  	    
		for (var i in this.comidas) {
		    this.comidas[i].draw(this.context);
		}
	}

	addSnake(id, color) {
		this.snakes[id] = new Snake();
		this.snakes[id].color = color;
	}

	updateSnake(id, snakeBody) {
		if (this.snakes[id]) {
			this.snakes[id].snakeBody = snakeBody;
		}
	}

	removeSnake(id) {
		this.snakes[id] = null;
		// Force GC.
		delete this.snakes[id];
	}

    //añadir y borrar comida
	addComida(id, x, y) {
	    this.comidas[id] = new Comida(id, x, y);
	}

	removeComida(id) {
	    this.comidas[id] = null;
	    delete this.comidas[id];
	}

	//Avisa cada vez que se tenga que repintar y llama al metodo draw
	run() {
	
		while ((new Date).getTime() > this.nextGameTick) {
			this.nextGameTick += this.skipTicks;
		}
		this.draw();
		if (this.nextFrame != null) {
			this.nextFrame();
		}
	}

	//Establece conexion, inicia el juego y ajusta cosas
	connect() {
		
		socket.onopen = () => {
			
		    //Enviar nombre y sala del que se quiere unir
		    sendMessage(events.outgoing.JOIN_GAME, { nombre: nombre, sala: sala });

			// Socket open.. start the game loop.
			Console.log('Info: WebSocket connection opened.');
			
		}

		socket.onclose = () => {
			Console.log('Info: WebSocket closed.');
			this.stopGameLoop();
		}

		socket.onmessage = (message) => {

			var packet = JSON.parse(message.data);
			
			switch (packet.type) {
			case 'update':
				for (var i = 0; i < packet.data.length; i++) {
					this.updateSnake(packet.data[i].id, packet.data[i].body);
				}
				break;
			case 'join':
				for (var j = 0; j < packet.data.length; j++) {
					this.addSnake(packet.data[j].id, packet.data[j].color);
				}
			    //Mostrar mensaje de espera para mas jugadores
				if (packet.snake === nombre) {
				    visibilidad('formulario');
				    visibilidad('esperando');
				}
				break;
			case 'failedJoin':			       
			        alert("Ya hay 4 jugadores en esta sala");
			        nombreInput.value = "";
			        salaInput.value = "";			        	        
			        break;
			case 'startGame':
			        empezarJuego();
			        break;
            //Ya hay dos jugadores y el creador puede iniciar la partida directamente
			case 'showButtonStart':
			        visibilidad('startBtn');
			        break;
		    case 'comidaCreada':
			        //Llega msj de comida creada con su id y posicion: añadirla al array
			        this.addComida(packet.id, packet.x, packet.y);
			        break;
            //Eliminar del mapa la comida
			case 'borrarComida':
			        this.removeComida(packet.id);
			        break;
            //Una serpiente ha muerto dos veces y hay que quitarla del mapa
			case 'leave':
			        this.removeSnake(packet.id);
			        nSnakes = packet.nSnakes;
			        console.log("Ha muerto la serpiente " + packet.nombre + " y quedan " + nSnakes + " vivas");
			        if (packet.nombre == nombre) {
			            alert('Has muerto 3 veces. Puntuacion: ' + packet.puntuacion);
			            viva = false;
			        }
			        
			        //Si soy la ultima serpiente viva, mandar msj para finalizar partida
			        if (nSnakes == 1 && viva) {
			            sendMessage(events.outgoing.FINPARTIDA, { nombre: nombre, sala: sala });
			        }
				    break;
			case 'dead':
				Console.log('Info: ¡Te has chocado!');
				this.direction = 'none';
				break;
			case 'kill':
				Console.log('Info: Head shot!');
				break;
            //Mostrar las puntuaciones finales y el ganador
			case 'finPartida':
			    var texto = "FIN DE LA PARTIDA \n";
			    texto += "Puntuaciones: \n";
			    for (var j = 0; j < packet.data.length; j++) {
			        texto += packet.data[j].nombre + ": " + packet.data[j].puntos + "\n";
			    }
			    texto += "GANADOR: " + packet.ganador;
			    alert(texto);
			    if (nombre == packet.ganador) {
			        this.removeSnake(packet.id);
			    }
			    this.stopGameLoop(); //finalizar el juego
			    break;
            //Actualizar el panel de hall de la fama
			case 'hallFama':		    
			    document.getElementById('hallFame').innerHTML = ""; //Limpiar
			    let puntos = '<b>' + 'HALL DE LA FAMA' + '</b>';
                
			    for (var j = 0; j < packet.data.length; j++) {
			        puntos += '<p>'+(j+1)+': '+packet.data[j].nombre + ': ' + packet.data[j].puntos + '<\p>';
			    }
			   
			    document.getElementById('hallFame').innerHTML = puntos;
			    break;
            //Actualizar el panel de puntos
			case 'puntos':
			    document.getElementById('puntos').innerHTML = ""; //Limpiar
			    let p = '<b>' + '<span style="color:white">' + 'PUNTUACIONES' + '</span>' + '</b>';
			    for (var j = 0; j < packet.data.length; j++) {
			        p += '<p>' + '<span style="color:' + packet.data[j].color+'">' + packet.data[j].nombre + ': ' + packet.data[j].puntos + '</span>'+'<\p>';
			    }
			    document.getElementById('puntos').innerHTML = p;
			    break;
            //Mostrar mensaje en la consola
			case 'info':
			    Console.log(packet.texto);
			    break;
			}
            
		}
	}
}

function empezarJuego() {
    //El jugador se ha unido correctamente, muestra pantalla de juego
    Console.log("Sala " + sala);
    Console.log("Se ha unido el jugador " + nombre);
    visibilidad('esperando');
    visibilidad('hallFameContainer');
    visibilidad('juego');

    //esto antes iba donde se abria el websocket
    Console.log('Info: Press an arrow key to begin.');

    game.startGameLoop();

    setInterval(() => sendMessage(events.outgoing.PING, { data: 'ping' }), 5000);
}


//Muestra u oculta elementos
function visibilidad(id) {
    var x = document.getElementById(id);
    if (x.style.display === 'none') {
        x.style.display = 'block';
    } else {
        x.style.display = 'none';
    }
}

//Inicialmente se ocultan el panel de juego, el mensaje de espera y el boton de empezar
visibilidad('juego'); 
visibilidad('esperando');
visibilidad('startBtn')

let nombreInput = document.querySelector('#nombre');
let salaInput = document.querySelector('#sala');
let nombre, sala;

document.getElementById('hallFame').innerHTML = '<b>' + 'HALL DE LA FAMA' + '</b>';


//Al pulsar el boton, coge el nombre del jugador y el de la sala
function clickJugar() {
    nombre = nombreInput.value.trim();
    sala = salaInput.value.trim();    

    socket = new WebSocket("ws://localhost:8080/snake");

    //Iniciar el juego
    game = new Game();
    game.initialize();  
    
};

function startBtn() {
    //enviar msj a host para saber que el juego empieza con boton
    sendMessage(events.outgoing.STARTED);
    empezarJuego(); //empieza juego para el que ha pulsado el boton
}

/*
function interrumpir() {
    sendMessage(events.outgoing.INTERRUMPIR);
}*/


    
