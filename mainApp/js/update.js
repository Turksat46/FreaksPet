/**
 * 
 */
(function (){
	var SCROLL_STEP = 50;
	page = document.getElementById("main");
	
	page.addEventListener('pagebeforeshow', function pageScrollHandler(e) {
        var page = e.target;
        elScroller = page.querySelector('.ui-scroller');

        /* Rotary event handler */
        rotaryEventHandler = function(e) {
            if (e.detail.direction === 'CW') {
                /* Right direction */
                elScroller.scrollTop += SCROLL_STEP;
            } else if (e.detail.direction === 'CCW') {
                /* Left direction */
                elScroller.scrollTop -= SCROLL_STEP;
            }
        };

        /* Register the rotary event */
        document.addEventListener('rotarydetent', rotaryEventHandler, false);

        /* Deregister the rotary event */
        page.addEventListener('pagebeforehide', function pageHideHandler() {
            page.removeEventListener('pagebeforehide', pageHideHandler, false);
            document.removeEventListener('rotarydetent', rotaryEventHandler, false);
        }, false);

    }, false);
} ());