(function () {
  const stage = document.getElementById("stage");
  const template = document.getElementById("slides");
  const progressFill = document.getElementById("progressFill");
  if (!stage || !template) return;

  const fragment = template.content.cloneNode(true);
  const slideEls = Array.from(fragment.querySelectorAll(".slide"));
  stage.appendChild(fragment);

  let idx = 0;

  function update() {
    slideEls.forEach(function (el, i) {
      el.classList.toggle("active", i === idx);
    });
    if (progressFill) {
      progressFill.style.width = ((idx + 1) / slideEls.length) * 100 + "%";
    }
    history.replaceState(null, "", "#" + (idx + 1));
  }

  function goto(n) {
    idx = Math.max(0, Math.min(slideEls.length - 1, n));
    update();
  }

  function next() { goto(idx + 1); }
  function prev() { goto(idx - 1); }

  function toggleFullscreen() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.().catch(function () {});
    } else {
      document.exitFullscreen?.().catch(function () {});
    }
  }

  function syncFullscreenHint() {
    document.body.classList.toggle("is-fullscreen", Boolean(document.fullscreenElement));
  }

  document.addEventListener("fullscreenchange", syncFullscreenHint);
  syncFullscreenHint();

  window.addEventListener("keydown", function (e) {
    if (e.key === "ArrowRight" || e.key === "PageDown" || e.key === " ") {
      e.preventDefault();
      next();
    } else if (e.key === "ArrowLeft" || e.key === "PageUp") {
      e.preventDefault();
      prev();
    } else if (e.key.toLowerCase() === "f") {
      toggleFullscreen();
    } else if (e.key.toLowerCase() === "p") {
      window.print();
    } else if (e.key === "Home") {
      goto(0);
    } else if (e.key === "End") {
      goto(slideEls.length - 1);
    }
  });

  var tx = 0;
  window.addEventListener("touchstart", function (e) {
    tx = e.touches[0].clientX;
  }, { passive: true });

  window.addEventListener("touchend", function (e) {
    var dx = e.changedTouches[0].clientX - tx;
    if (Math.abs(dx) > 50) {
      if (dx < 0) next();
      else prev();
    }
  }, { passive: true });

  window.addEventListener("hashchange", function () {
    var h = Number.parseInt(window.location.hash.replace("#", ""), 10);
    if (Number.isFinite(h) && h >= 1) goto(h - 1);
  });

  var hash = Number.parseInt(window.location.hash.replace("#", ""), 10);
  goto(Number.isFinite(hash) && hash >= 1 ? hash - 1 : 0);
})();
