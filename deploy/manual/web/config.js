const response = await fetch("/inspect/config");
if (!response.ok) throw new Error("Lab configuration unavailable");
export const settings = await response.json();
export const overview = settings.grafanaUrl + "/d/locker-overview/locker-overview";

for (const link of document.querySelectorAll("[data-tool]")) {
  link.href = link.dataset.tool === "mailpit" ? settings.mailpitUrl : overview;
}
