let frameEle;

/**
 * append a button to ul element
 */
function appendBtn(ulId, text, clickFun, spanIcon, href, id) {
  const rootEle = document.getElementById(ulId);
  // create span element and attribute data-feather is spanIcon
  let span = document.createElement('span');
  // default spanIcon is chevron-right
  spanIcon = spanIcon ? spanIcon : 'chevron-right';
  span.setAttribute('data-feather', spanIcon);

  // create a element and attribute onclick is clickFun and class is nav-link
  let a = document.createElement('a');
  let onMenuClick = clickFun;
  if (!onMenuClick) {
    onMenuClick = () => {
      activeSideMenu(text);
      document.getElementById('zOfficeDoc').innerHTML = '';
      setIframeSrc(href);
    };
  }
  a.onclick = onMenuClick;
  // add data-href attribute
  a.className = 'nav-link';
  if (id) a.id = id;

  // create li element and attribute class is nav-item
  let li = document.createElement('li');
  li.className = 'nav-item';

  // append span and text to a element
  a.append(span);
  a.append(text);

  // append a element to li element
  li.append(a);

  // append li element to ul element
  rootEle.append(li);

  // call feather.replace() to render icon
  feather.replace();
}

// function to get frameEle
function getFrameEle() {
  if (!frameEle) frameEle = document.getElementById("integration-frame");
  return frameEle;
}

function activeSideMenu(text) {
  // drive-list中后代a元素，被选中增加active
  const fun = (id) => {
    const driveList = document.getElementById(id);
    const aList = driveList.getElementsByTagName('a');
    for (let i = 0; i < aList.length; i++) {
      aList[i].classList.remove('active');
      if (aList[i].innerText === text) {
        aList[i].classList.add('active');
      }
    }
  }
  fun('drive-list')
}

const sideMenu = [
  {
    menuId: 'drive-list',
    subMenu: [
      {
        id: 'local',
        text: '本地仓库',
        icon: 'folder',
        href: '/home/local'
      }
    ]
  }
]

function initSideMenu() {
  sideMenu.forEach(menu => {
    const menuId = menu.menuId;
    menu.subMenu.forEach(subMenu => {
      appendBtn(menuId, subMenu.text, subMenu.clickFun, subMenu.icon, subMenu.href, subMenu.id);
    })
  })
}

/**
 * 监听消息，判断是错误提示还是以iframe打开文档
 * 消息发布处于drive.js文件中
 * 1、openInCurrentTab
 * 2、showToastInParent
 */
window.addEventListener('message', function (event) {
  const data = event.data;
  if (data.type === 'showToast') {
    showToast(data.msg);
  } else if (data.type === 'updateFrameSrc') {
    setIframeSrc(data.msg.url);
  }
});

// function to set the iframe src
const setIframeSrc = (url) => {
  // 显示loading-div，隐藏iframe
  const loadingDiv = document.getElementById('loading-div');
  // 去除loading-div的display属性
  loadingDiv.style.display = '';

  const frameEle = getFrameEle();
  frameEle.src = url;
}

// function to show toast
const showToast = (msg) => {
  const el = document.createElement("div");
  el.setAttribute("style", "position:absolute;top:8%;left:45%");
  el.setAttribute("role", "alert");
  // set el class
  el.className = 'alert alert-dark';

  el.innerHTML = msg;
  setTimeout(function () {
    el.parentNode.removeChild(el);
  }, 4000);
  document.body.appendChild(el);
}

$(() => {
  // iframe加载完成后，隐藏loading-div，显示iframe
  const integrationFrame = document.getElementById('integration-frame');
  integrationFrame.onload = function () {
    const loadingDiv = document.getElementById('loading-div');
    loadingDiv.style.display = 'none';
  }

  // append refresh and close button
  initSideMenu();

  feather.replace();
})

