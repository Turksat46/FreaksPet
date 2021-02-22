(function() {

    //Check if it is first time visiting the app
    if (tizen.preference.getValue("firsttime") == "false") {
        //TODO: Make Tutorial
    	console.log("######## User is NEW! Show Update-Log! ########");
    	location.replace("updatelogger.html");
        //Set visitor to amateur
        tizen.preference.setValue("firsttime", "true");
    }


    console.log("######## Starting Game-Engine! ########");
    tizen.power.request("SCREEN", "SCREEN_NORMAL");

    var debugtimer = setInterval(function() {
        var sleepingstatus = tizen.preference.getValue('sleeping');
        console.log("Sleppingstatus: " + sleepingstatus);
    }, 1000);

    if (tizen.preference.getValue("sleeping") == "true") {
        //TODO: Set sleeping gif here
        var face = document.getElementById("faceholder");
        face.src = "FreaksPetSleeping.gif";
    } else {
        var face = document.getElementById("faceholder");
        face.src = "FreaksPetBlinzelOpenMouth.gif";
    }

    

    //Notificationfuction (not copied from internet)
    function sendNotify(title, notifycontent) {
        try {
            console.log("######## Preparing notification #######");
            var appControl = new tizen.ApplicationControl(
                "http://tizen.org/appcontrol/operation/view", null, "image/jpg", null);

            var notificationGroupDict = {
                content: notifycontent,
                actions: {
                    soundPath: "music/Over the horizon.mp3",
                    vibration: true,
                    appControl: appControl
                }
            };

            /* Constructs and posts the simple user notification. */
            var notification =
                new tizen.UserNotification("SIMPLE", title, notificationGroupDict);
            console.log("######## Sending Notification! ########");
            tizen.notification.post(notification);
        } catch (err) {
            console.log(err.name + ": " + err.message);
        }
    }
    //
    //MAIN TIMER
    //
    var timer = setInterval(function() {
    	//Checking for shaking for wakeup-szenario
        var accelerationSensor = tizen.sensorservice.getDefaultSensor("ACCELERATION");

        function onGetSuccessCB(sensorData) {
            console.log("######## Get Necessary Sensor Data ########");
            console.log("x: " + sensorData.x);
            console.log("y: " + sensorData.y);
            console.log("z: " + sensorData.z);

            if (!tizen.preference.exists('sleeping')) {
                console.log("!!!######## Sleeping data does not exist! ########");
            }
            //check if pet is sleeping
            if (tizen.preference.getValue('sleeping') === 'true') {
                //wake up pet, when movement is above 10
                var maxmov = 15.0;
                if (sensorData.x > maxmov || sensorData.x < -maxmov || sensorData.y > maxmov || sensorData.y < -maxmov || sensorData.z > maxmov || sensorData.z < -maxmov) {

                    //Wake-Up procedure
                    console.log("### OVER 15 ###");
                    //Set awake to default
                    tizen.preference.setValue('sleeping', 'false');
                    //WAKE UP PLEASEEEE
                    wakeup();
                }
            }

            //Checking light for sleep-szenario
            var lightSensor = tizen.sensorservice.getDefaultSensor("LIGHT");

            function onGetSuccessCB(sensorData) {
                //console.log("######### Lightlevel ########");
                console.log("Light level: " + sensorData.lightLevel);
            }

            function onerrorCB(error) {
                console.log("Error occurred");
            }

            function onsuccessCB() {
                console.log("LightSensor start");
                lightSensor.getLightSensorData(onGetSuccessCB, onerrorCB);
            }

            lightSensor.start(onsuccessCB);
        }

        function onerrorCB(error) {
            console.log("Error occurred: " + error.message);
            sendNotify("FreaksPet", "ERROR: Couldn't get AccelerationSensor-Data! Please restart the app!");
            tizen.application.getCurrentApplication().exit();
        }

        function onsuccessCB() {
            console.log("Getting SensorData");
            accelerationSensor.getAccelerationSensorData(onGetSuccessCB, onerrorCB);
        }

        accelerationSensor.start(onsuccessCB);
        
        
        //BatteryChecker
        var battery = navigator.battery || navigator.webkitBattery || navigator.mozBattery;
        if(battery.charging){
        	console.log("Charging state: Charging");
        	//falltocharging();
        }else{
        	console.log("Charging state: Discharging");
        	//wakeup();
        }
        
        
    }, 1000);

    var lighttimer = setInterval(function() {

        var lightSensor = tizen.sensorservice.getDefaultSensor("LIGHT");

        function onGetSuccessCB(sensorData) {
            console.log("######### Checking Lightlevel For Sleeping ########");
            console.log("######## Light level was: " + sensorData.lightLevel);
            //Put pet to sleep
            //TODO: Add Animations 
            if (tizen.preference.getValue("sleeping") == "false") {
                if (sensorData.lightLevel < 9) {
                    //Set sleep as default
                    tizen.preference.setValue('sleeping', 'true');
                    //GOOD NIGHT MY LITTLE FRIEND
                    fallasleep();
                } else {
                    //Do nothing...
                }
            }

        }
        
        

        function onerrorCB(error) {
            console.log("Error occurred");
        }

        function onsuccessCB() {
            console.log("Sensor start");
            lightSensor.getLightSensorData(onGetSuccessCB, onerrorCB);
        }

        lightSensor.start(onsuccessCB);
    }, 60000);

    //ANIMATIONFUNCTIONS
    function fallasleep() {
        //clearInterval(timer);
        //sendNotify("FreaksPet", "Pet is falling asleep!");
        var face = document.getElementById("faceholder");
        face.src = "FreaksPetFallingAsleep.gif";
        //tizen.power.setScreenBrightness(0.5);
        var sleepanimator = setTimeout(function() {
            face.src = "FreaksPetSleeping.gif";
            clearTimeout(sleepanimator);
        }, 11700);
    }

    function wakeup() {
        var face = document.getElementById("faceholder");
        face.src = "FreaksPetBlinzelOpenMouth.gif";
    }

    function falltocharging() {
    	//clearInterval(timer);
        //sendNotify("FreaksPet", "Pet is falling asleep!");
        var face = document.getElementById("faceholder");
        if(face.src=="FreaksPetFallingAsleep.gif" || face.src == "FreaksPetSleeping.gif"){
        	//TODO: Replace with chargesleep
        	face.src="FreaksPetChargeSleep";
        }else{
        	tizen.preference.setValue("sleeping", "true");
        face.src = "FreaksPetFallingAsleep.gif";
        //tizen.power.setScreenBrightness(0.5);
        var sleepanimator = setTimeout(function() {
        	//TODO: Make a animation for charging
            face.src = "FreaksPetChargeSleep.gif";
            clearTimeout(sleepanimator);
        }
        , 11700);
        }
    }
    
    //END ANIMATIONFUNCTIONS

    //Change to thumbnails



    window.addEventListener("tizenhwkey", function(ev) {
        var activePopup = null,
            page = null,
            pageId = "";

        if (ev.keyName === "back") {
            sendNotify("FreaksPet", "Come back soon! Successfully closed application.");
            console.log("######## !USER CLOSED THE APPLICATION! ########");
            tizen.power.release("SCREEN");
            tizen.application.getCurrentApplication().exit();
        }

    });
}());