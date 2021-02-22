(function() {
	tizen.power.request('SCREEN', 'SCREEN_NORMAL');
	console.log("###### Setting display to ON ######");
	var sec = 5;
	tizen.preference.setValue("sleeping", "false");
	console.log("###### Preference sleeping set! ######");
	tizen.preference.setValue("firsttime", "false");
	console.log("###### Preference firsttime set! ######");
    var timer = setInterval(function(){
        sec--;
        if (sec < 0) {
            clearInterval(timer);
            location.replace("game.html");
        }
    }, 1000);
}());