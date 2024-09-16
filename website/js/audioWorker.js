let intervalId;
let updateInterval = 15;

self.onmessage = function(e) {
  if (e.data.type === "start") {
    startWorker();
  } else if (e.data.type === "stop") {
    stopWorker();
  } else if (e.data.type === "setUpdateInterval") {
    updateInterval = e.data.interval;
  }
};

function startWorker() {
  intervalId = setInterval(checkTime, 1000);
}

function stopWorker() {
  clearInterval(intervalId);
}

function checkTime() {
  const now = new Date();
  const minutes = now.getMinutes();
  const seconds = now.getSeconds();

  if (seconds === 0) {
    self.postMessage({ type: "tick" });
    
    // Calculate the next update time
    const nextUpdateMinute = Math.ceil(minutes / updateInterval) * updateInterval;
    
    if (minutes === nextUpdateMinute % 60) {
      self.postMessage({ type: "update", time: now });
    }
  }
}
