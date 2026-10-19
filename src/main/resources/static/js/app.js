(function () {
  'use strict';

  /* ---------- 로그인 전: 좁은 화면 햄버거 드롭다운 ---------- */
  var toggle = document.querySelector('.menu-toggle:not(.menu-toggle--mega)');
  var nav = document.getElementById('site-nav');
  if (toggle && nav) {
    var setOpen = function (open) {
      toggle.setAttribute('aria-expanded', String(open));
      nav.classList.toggle('is-open', open);
    };

    toggle.addEventListener('click', function () {
      setOpen(toggle.getAttribute('aria-expanded') !== 'true');
    });

    nav.addEventListener('click', function (e) {
      if (e.target.closest('a')) setOpen(false);
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && toggle.getAttribute('aria-expanded') === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });
  }

  /* ---------- 로그인 후: 전체 메뉴 ---------- */
  var megaToggle = document.querySelector('.menu-toggle--mega');
  var mega = document.getElementById('mega-menu');
  if (megaToggle && mega) {
    var setMega = function (open, returnFocus) {
      megaToggle.setAttribute('aria-expanded', String(open));
      mega.hidden = !open;
      if (open) {
        var first = mega.querySelector('a');
        if (first) first.focus({ preventScroll: true });
      } else if (returnFocus) {
        megaToggle.focus();
      }
    };

    megaToggle.addEventListener('click', function () {
      setMega(megaToggle.getAttribute('aria-expanded') !== 'true', false);
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !mega.hidden) setMega(false, true);
    });

    // 메뉴 밖을 누르면 닫는다
    document.addEventListener('click', function (e) {
      if (!mega.hidden && !e.target.closest('.mega') && !e.target.closest('.menu-toggle--mega')) setMega(false, false);
    });

    // 뒤로 가기(bfcache)로 돌아왔을 때 열린 채로 남지 않게 한다
    window.addEventListener('pageshow', function () {
      if (!mega.hidden) setMega(false, false);
    });
  }

  /* ---------- 서버 응답 대기 로딩 (data-loading 폼) ---------- */
  var loading = document.getElementById('loading');
  var loadingText = document.getElementById('loading-text');
  var loadingForms = document.querySelectorAll('form[data-loading]');

  function hideLoading() {
    if (!loading) return;
    loading.hidden = true;
    loadingForms.forEach(function (f) {
      var b = f.querySelector('button[type="submit"]');
      if (b) b.disabled = false;
    });
  }

  if (loading) {
    loadingForms.forEach(function (form) {
      form.addEventListener('submit', function () {
        if (!form.checkValidity()) return;
        loadingText.textContent = form.getAttribute('data-loading');
        loading.hidden = false;
        // 제출이 시작된 뒤에 비활성화해야 폼 값이 함께 전송된다
        setTimeout(function () {
          var b = form.querySelector('button[type="submit"]');
          if (b) b.disabled = true;
        }, 0);
      });
    });
    // 뒤로 가기(bfcache)로 돌아왔을 때 로딩이 남지 않게 한다
    window.addEventListener('pageshow', hideLoading);
  }

  /* ---------- 비밀번호 조건·일치 표시 ---------- */
  var pwForm = document.querySelector('form[data-password-form]');
  if (pwForm) {
    var pw = pwForm.querySelector('input[name="password"]');
    var confirm = pwForm.querySelector('input[name="passwordConfirm"]');
    var hint = pwForm.querySelector('[data-match-hint]');
    var rules = {
      length: function (v) { return v.length >= 8; },
      letter: function (v) { return /[A-Za-z]/.test(v); },
      digit: function (v) { return /\d/.test(v); },
      special: function (v) { return /[!-\/:-@\[-`{-~]/.test(v); }
    };
    var idleHint = hint.textContent;

    var update = function () {
      Object.keys(rules).forEach(function (name) {
        var li = pwForm.querySelector('[data-rule="' + name + '"]');
        if (li) li.classList.toggle('is-met', rules[name](pw.value));
      });
      hint.classList.remove('is-ok', 'is-bad');
      if (!confirm.value) {
        hint.textContent = idleHint;
      } else if (confirm.value === pw.value) {
        hint.textContent = '비밀번호가 일치합니다.';
        hint.classList.add('is-ok');
      } else {
        hint.textContent = '비밀번호가 일치하지 않습니다.';
        hint.classList.add('is-bad');
      }
    };
    pw.addEventListener('input', update);
    confirm.addEventListener('input', update);
  }
})();
