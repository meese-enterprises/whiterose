document.addEventListener("DOMContentLoaded", () => {
  class Whiterose {
    constructor() {
      this.clockElement = document.getElementById("clock");
      this.tickSound = null;
      this.lastUpdate = -1;
      this.audioEnabled = false;
      this.audioToggle = document.getElementById("audioToggle");
      this.audioToggle.textContent = "🔇";
      this.updateInterval = 15; // Default interval in minutes
      this.updateIntervalInput = document.getElementById("updateInterval");
      this.setIntervalButton = document.getElementById("setInterval");
      this.themeToggle = document.getElementById("themeToggle");

      this.setupEventListeners();
      this.start();
      this.loadSavedTheme();
    }

    setupEventListeners() {
      document.addEventListener("visibilitychange", () => this.handleVisibilityChange());
      this.audioToggle.addEventListener("click", () => this.toggleAudio());
      this.setIntervalButton.addEventListener("click", () => this.setCustomInterval());
      document.getElementById("settingsIcon").addEventListener("click", () => this.openSettingsModal());
      document.querySelector(".close").addEventListener("click", () => this.closeSettingsModal());
      window.addEventListener("click", (event) => this.closeModalOnOutsideClick(event));
      this.themeToggle.addEventListener("change", () => this.switchTheme());
    }

    async loadTickSound() {
      try {
        const response = await fetch("./audio/tick.mp3");
        const arrayBuffer = await response.arrayBuffer();
        this.tickSound = await this.audioContext.decodeAudioData(arrayBuffer);
        console.log("Tick sound loaded successfully");
      } catch (error) {
        console.error("Error loading tick sound:", error);
      }
    }

    start() {
      this.update();
      this.updateInterval = setInterval(() => this.update(), 1000);
    }

    update() {
      const now = new Date();
      const hours = now.getHours().toString().padStart(2, "0");
      const minutes = now.getMinutes().toString().padStart(2, "0");
      const seconds = now.getSeconds().toString().padStart(2, "0");
    
      this.clockElement.textContent = `${hours}:${minutes}:${seconds}`;
    
      const currentMinutes = now.getHours() * 60 + now.getMinutes();
      const currentSeconds = now.getSeconds();
    
      if (currentSeconds === 0 && (currentMinutes % this.updateInterval === 0) && currentMinutes !== this.lastUpdate) {
        this.lastUpdate = currentMinutes;
        this.speakTime(hours, minutes);
      } else {
        this.playTickSound();
      }
    }

    playTickSound() {
      const now = new Date();
      if (now.getSeconds() === 0 && this.audioEnabled && this.tickSound && this.audioContext && this.audioContext.state === "running") {
        const source = this.audioContext.createBufferSource();
        source.buffer = this.tickSound;
        source.connect(this.audioContext.destination);
        console.debug("Playing tick sound");
        source.start();
      }
    }

    speakTime(hours, minutes) {
      if (this.audioEnabled) {
        const timeString = this.formatTimeForSpeech(hours, minutes);
        console.debug("Speaking time:", timeString);
        const utterance = new SpeechSynthesisUtterance(timeString);
        speechSynthesis.speak(utterance);
      }
    }

    formatTimeForSpeech(hours, minutes) {
      const h = parseInt(hours);
      const m = parseInt(minutes);
      let timeString = `It's ${h === 0 ? 12 : h > 12 ? h - 12 : h}`;

      if (m !== 0) {
        timeString += ` ${m}`;
      }

      return timeString + (h >= 12 ? " PM" : " AM");
    }

    toggleAudio() {
      this.audioEnabled = !this.audioEnabled;
      this.audioToggle.textContent = this.audioEnabled ? "🔊" : "🔇";
      if (this.audioEnabled) {
        if (!this.audioContext) {
          this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        this.audioContext.resume().then(() => {
          console.log("AudioContext resumed successfully");
          if (!this.tickSound) {
            this.loadTickSound();
          }
        });
      } else {
        if (this.audioContext) {
          this.audioContext.suspend();
        }
      }
    }

    handleVisibilityChange() {
      if (document.hidden) {
        clearInterval(this.updateInterval);
      } else {
        this.updateInterval = setInterval(() => this.update(), 1000);
      }
    }

    setCustomInterval() {
      const newInterval = parseInt(this.updateIntervalInput.value);
      if (newInterval >= 1 && newInterval <= 60) {
        this.updateInterval = newInterval;
        console.log(`Update interval set to ${this.updateInterval} minutes`);
        this.closeSettingsModal();
      } else {
        console.error("Invalid interval. Please enter a number between 1 and 60.");
      }
    }

    openSettingsModal() {
      document.getElementById("settingsModal").style.display = "block";
    }

    closeSettingsModal() {
      document.getElementById("settingsModal").style.display = "none";
    }

    closeModalOnOutsideClick(event) {
      const modal = document.getElementById("settingsModal");
      if (event.target === modal) {
        modal.style.display = "none";
      }
    }

    switchTheme() {
      const theme = this.themeToggle.value;
      document.body.classList.remove("cream-theme", "dark-theme");
      if (theme === "cream") {
        document.body.classList.add("cream-theme");
      } else if (theme === "dark") {
        document.body.classList.add("dark-theme");
      }
      localStorage.setItem("theme", theme);
    }

    loadSavedTheme() {
      const savedTheme = localStorage.getItem("theme") || "white";
      this.themeToggle.value = savedTheme;
      this.switchTheme();
    }
  }

  new Whiterose();
});
