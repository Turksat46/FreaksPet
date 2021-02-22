(function () {
	
	console.log("######## STARTUP FREAKSPET ########");
	tizen.power.request('SCREEN', 'SCREEN_NORMAL');
	console.log("###### Set Display to ON ######");
	//Set a timer, just for fun
	var sec = 5;
    var timer = setInterval(function(){
        sec--;
        if (sec < 0) {
            clearInterval(timer);
            
            //Check, if it is first time visiting the app
            if(!tizen.preference.exists("firsttime")){
            	console.log("######## SETUP ########");
        		location.replace("firsttime.html");
        	}else{
        		location.replace("game.html");
        	}
        }
    }, 1000);
    
	window.addEventListener("tizenhwkey", function (ev) {
		

		if (ev.keyName === "back") {
			tizen.application.getCurrentApplication().exit();
		}
	});
	
	console.log("######### SoundPlayer ########");
	audio = document.createElement('audio');
    audio.src = "sounds/startup.ogg";
    audio.play();
    console.log("######## Played sound! #########");
}());