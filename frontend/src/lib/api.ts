import type { components, paths } from "../generated/organization";
import type { paths as DirectoryPaths } from "../generated/directory";

type OrganizationSchema = components["schemas"]["Organization"];
type OrganizationInputSchema = components["schemas"]["OrganizationInput"];
export type Organization = Omit<
  OrganizationSchema,
  "coordinates" | "postalAddress" | "annualTurnover" | "fullName"
> & {
  x: OrganizationSchema["coordinates"]["x"];
  y: OrganizationSchema["coordinates"]["y"];
  annualTurnover: number | null;
  fullName: string | null;
  street: string;
};

const organizationsPath = "/organizations" satisfies keyof paths;
const directoryFilterPath =
  "/filter/employees/{min-employees-count}/{max-employees-count}" satisfies keyof DirectoryPaths;

export type Filters = {
  name: string;
  type: string;
  sortBy: string;
  sortOrder: string;
  page: number;
  size: number;
  fields?: Record<string, string>;
};
const root = (import.meta.env.VITE_API_BASE ?? "").replace(/\/$/, "");

function parseXml(source: string) {
  const doc = new DOMParser().parseFromString(source, "application/xml");
  const error = doc.querySelector("parsererror");
  if (error) throw new Error("The service returned invalid XML.");
  return doc;
}

function text(node: ParentNode, selector: string) {
  return node.querySelector(selector)?.textContent?.trim() ?? "";
}

function parseOrganization(node: Element): Organization {
  return {
    id: Number(node.getAttribute("id") ?? text(node, "id")),
    name: text(node, "name"),
    x: Number(text(node, "coordinates > x")),
    y: Number(text(node, "coordinates > y")),
    creationDate: text(node, "creationDate"),
    annualTurnover: text(node, "annualTurnover")
      ? Number(text(node, "annualTurnover"))
      : null,
    fullName: text(node, "fullName") || null,
    type: text(node, "type") as OrganizationSchema["type"],
    street: text(node, "postalAddress > street"),
  };
}

async function request(path: string, init?: RequestInit) {
  const response = await fetch(`${root}${path}`, init);
  const body = await response.text();
  if (!response.ok) {
    let message = body || response.statusText;
    try {
      message = text(parseXml(body), "message") || message;
    } catch {
      /* keep raw response */
    }
    throw new Error(`${response.status} ${response.statusText}: ${message}`);
  }
  return body;
}

export async function getOrganizations(filters: Filters) {
  const params = new URLSearchParams();
  if (filters.name.trim()) params.set("name", filters.name.trim());
  if (filters.type) params.set("type", filters.type);
  if (filters.sortBy) params.append("sortBy", filters.sortBy);
  if (filters.sortOrder) params.set("sortOrder", filters.sortOrder);
  for (const [name, value] of Object.entries(filters.fields ?? {})) {
    if (value.trim()) params.set(name, value.trim());
  }
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  const doc = parseXml(await request(`${organizationsPath}?${params}`));
  return {
    items: Array.from(doc.querySelectorAll("organizations > organization")).map(
      parseOrganization,
    ),
    total: Number(text(doc, "organizations > total") || 0),
  };
}

export type OrganizationInput = Omit<
  OrganizationInputSchema,
  "coordinates" | "postalAddress" | "annualTurnover" | "fullName"
> &
  Pick<Organization, "x" | "y" | "street" | "annualTurnover" | "fullName">;
export function organizationXml(value: OrganizationInput) {
  const escape = (v: string) =>
    v
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&apos;");
  return `<organization><name>${escape(value.name)}</name><coordinates><x>${value.x}</x><y>${value.y}</y></coordinates>${value.annualTurnover == null ? "" : `<annualTurnover>${value.annualTurnover}</annualTurnover>`}${value.fullName ? `<fullName>${escape(value.fullName)}</fullName>` : ""}<type>${value.type}</type><postalAddress><street>${escape(value.street)}</street></postalAddress></organization>`;
}

export async function saveOrganization(value: OrganizationInput, id?: number) {
  const body = await request(id ? `/organizations/${id}` : "/organizations", {
    method: id ? "PUT" : "POST",
    headers: { "Content-Type": "application/xml", Accept: "application/xml" },
    body: organizationXml(value),
  });
  return parseOrganization(parseXml(body).querySelector("organization")!);
}

export async function deleteOrganization(id: number) {
  await request(`/organizations/${id}`, {
    method: "DELETE",
    headers: { Accept: "application/xml" },
  });
}

export async function getOrganization(id: number) {
  const doc = parseXml(await request(`/organizations/${id}`));
  return parseOrganization(doc.documentElement);
}

export type Employee = components["schemas"]["Employee"];
export type EmployeeInput = components["schemas"]["EmployeeInput"];
export async function getEmployees(id: number): Promise<Employee[]> {
  const doc = parseXml(await request(`/organizations/${id}/employees`));
  return Array.from(doc.querySelectorAll("employees > employee")).map(
    (node) => ({
      id: Number(node.getAttribute("id")),
      organizationId: Number(node.getAttribute("organizationId")),
      name: text(node, "name"),
      position: text(node, "position") || null,
      salary: text(node, "salary") ? Number(text(node, "salary")) : null,
    }),
  );
}

export async function addEmployee(id: number, value: EmployeeInput) {
  const escape = (s: string) =>
    s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
  await request(`/organizations/${id}/employees`, {
    method: "POST",
    headers: { "Content-Type": "application/xml", Accept: "application/xml" },
    body: `<employee><name>${escape(value.name)}</name>${value.position ? `<position>${escape(value.position)}</position>` : ""}${value.salary == null ? "" : `<salary>${value.salary}</salary>`}</employee>`,
  });
}

export async function deleteEmployee(id: number, employeeId: number) {
  await request(`/organizations/${id}/employees/${employeeId}`, {
    method: "DELETE",
  });
}

export async function directoryFilter(min: number, max: number) {
  const path = directoryFilterPath
    .replace("{min-employees-count}", String(min))
    .replace("{max-employees-count}", String(max));
  const doc = parseXml(await request(`/orgdirectory${path}`));
  return Array.from(doc.querySelectorAll("organizations > organization")).map(
    parseOrganization,
  );
}

export async function directoryOrder(field: string, desc: boolean) {
  const doc = parseXml(
    await request(`/orgdirectory/order/${encodeURIComponent(field)}/${desc}`),
  );
  return Array.from(doc.querySelectorAll("organizations > organization")).map(
    parseOrganization,
  );
}

export async function getStats(threshold = 1_000_000) {
  const [groupsXml, countXml, uniqueXml] = await Promise.all([
    request("/organizations/stats/grouped-by-name"),
    request(`/organizations/stats/annual-turnover/less-than/${threshold}`),
    request("/organizations/stats/annual-turnover/unique"),
  ]);
  const groupsDoc = parseXml(groupsXml);
  const countDoc = parseXml(countXml);
  const uniqueDoc = parseXml(uniqueXml);
  return {
    groups: Array.from(groupsDoc.querySelectorAll("groups > group")).map(
      (node) => ({
        name: text(node, "name"),
        count: Number(text(node, "count")),
      }),
    ),
    belowThreshold: Number(text(countDoc, "count") || 0),
    uniqueTurnovers: Array.from(
      uniqueDoc.querySelectorAll("values > value"),
    ).map((node) =>
      node.getAttribute("xsi:nil") === "true" ? null : Number(node.textContent),
    ),
  };
}
