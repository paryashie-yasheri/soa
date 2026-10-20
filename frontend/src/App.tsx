import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type Table,
} from "@tanstack/react-table";
import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "./components/ui/button";
import {
  Activity,
  ArrowDown,
  ArrowDownUp,
  ArrowUp,
  BookOpen,
  Building2,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Code2,
  Filter,
  Gauge,
  GitBranch,
  LoaderCircle,
  Plus,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  Users,
  X,
} from "lucide-react";
import {
  addEmployee,
  deleteEmployee,
  getEmployees,
  getOrganization,
  deleteOrganization,
  directoryFilter,
  directoryOrder,
  getOrganizations,
  getStats,
  saveOrganization,
  type Filters,
  type Organization,
  type OrganizationInput,
} from "./lib/api";

type Page = "organizations" | "directory" | "insights";
const initialFilters: Filters = {
  name: "",
  type: "",
  sortBy: "id",
  sortOrder: "asc",
  page: 1,
  size: 10,
};
const typeOptions = [
  "COMMERCIAL",
  "TRUST",
  "PRIVATE_LIMITED_COMPANY",
  "OPEN_JOINT_STOCK_COMPANY",
];
const fieldOptions = [
  "id",
  "name",
  "coordinates.x",
  "coordinates.y",
  "creationDate",
  "annualTurnover",
  "fullName",
  "type",
  "postalAddress.street",
];

function App() {
  const [page, setPage] = useState<Page>("organizations");
  const [filters, setFilters] = useState(initialFilters);
  const [search, setSearch] = useState("");
  const [type, setType] = useState("");
  const [editor, setEditor] = useState<Organization | "new" | null>(null);
  const [notice, setNotice] = useState("");
  const [employeeOrg, setEmployeeOrg] = useState<Organization | null>(null);
  const [showFilters, setShowFilters] = useState(false);
  const [extraFilters, setExtraFilters] = useState<Record<string, string>>({});
  const [threshold, setThreshold] = useState(1_000_000);
  const client = useQueryClient();
  const list = useQuery({
    queryKey: ["organizations", filters],
    queryFn: () => getOrganizations(filters),
  });
  const stats = useQuery({
    queryKey: ["stats", threshold],
    queryFn: () => getStats(threshold),
    enabled: page === "insights",
  });
  const mutation = useMutation({
    mutationFn: (input: { value: OrganizationInput; id?: number }) =>
      saveOrganization(input.value, input.id),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["organizations"] });
      setEditor(null);
      setNotice("Organization saved successfully.");
    },
    onError: (error) => setNotice(error.message),
  });
  const inspect = useMutation({
    mutationFn: getOrganization,
    onSuccess: setEditor,
    onError: (error) => setNotice(error.message),
  });
  const remove = useMutation({
    mutationFn: deleteOrganization,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["organizations"] });
      setNotice("Organization deleted.");
    },
    onError: (error) => setNotice(error.message),
  });
  const columns = useMemo<ColumnDef<Organization>[]>(
    () => [
      {
        accessorKey: "id",
        header: "ID",
        cell: (info) => (
          <span className="mono muted">#{info.getValue<number>()}</span>
        ),
      },
      {
        accessorKey: "name",
        header: "Organization",
        cell: ({ row }) => (
          <div className="org-cell">
            <span className="org-mark">
              {row.original.name.slice(0, 1).toUpperCase()}
            </span>
            <div>
              <div className="strong">{row.original.name}</div>
              <div className="subtle">
                {row.original.fullName || "No legal name"}
              </div>
            </div>
          </div>
        ),
      },
      {
        accessorKey: "type",
        header: "Type",
        cell: (info) => (
          <span className="type-badge">
            {String(info.getValue()).replaceAll("_", " ").toLowerCase()}
          </span>
        ),
      },
      {
        id: "location",
        header: "Location",
        cell: ({ row }) => (
          <div>
            <div>{row.original.street || "—"}</div>
            <div className="subtle mono">
              {row.original.x}, {row.original.y}
            </div>
          </div>
        ),
      },
      {
        accessorKey: "annualTurnover",
        header: "Turnover",
        cell: (info) =>
          info.getValue<number | null>() == null ? (
            <span className="muted">Not set</span>
          ) : (
            <span className="mono">
              {Number(info.getValue()).toLocaleString("en-US")}
            </span>
          ),
      },
      {
        accessorKey: "creationDate",
        header: "Created",
        cell: (info) => (
          <span className="muted">{info.getValue<string>() || "—"}</span>
        ),
      },
      {
        id: "actions",
        header: "",
        cell: ({ row }) => (
          <div className="row-actions">
            <button
              className="icon-btn"
              title="Manage employees"
              onClick={() => setEmployeeOrg(row.original)}
            >
              <Users size={15} />
            </button>
            <button
              className="icon-btn"
              title="Edit organization"
              onClick={() => inspect.mutate(row.original.id)}
            >
              <SlidersHorizontal size={15} />
            </button>
            <button
              className="icon-btn danger"
              title="Delete organization"
              onClick={() => {
                if (window.confirm(`Delete ${row.original.name}?`))
                  remove.mutate(row.original.id);
              }}
            >
              <Trash2 size={15} />
            </button>
          </div>
        ),
      },
    ],
    [remove, inspect],
  );
  const table = useReactTable({
    data: list.data?.items ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  function applyFilters() {
    setFilters((current) => ({
      ...current,
      name: search,
      type,
      fields: extraFilters,
      page: 1,
    }));
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <aside className="sidebar">
        <a
          href="#"
          className="brand"
          onClick={(e) => {
            e.preventDefault();
            setPage("organizations");
          }}
        >
          <span className="brand-icon">
            <Building2 size={19} />
          </span>
          <span>
            orbit<span className="brand-dot">.</span>
            <small>API WORKSPACE</small>
          </span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <div className="service-card">
          <span className="service-pulse" />
          <span>
            <strong>Organization API</strong>
            <small>v1.0.0 · XML</small>
          </span>
          <ChevronDown size={15} className="muted" />
        </div>
        <nav className="nav">
          <div className="nav-label">EXPLORE</div>
          <button
            aria-current={page === "organizations" ? "page" : undefined}
            className={`nav-item ${page === "organizations" ? "active" : ""}`}
            onClick={() => setPage("organizations")}
          >
            <Building2 size={17} /> Organizations{" "}
            <span className="nav-count">{list.data?.total ?? "—"}</span>
          </button>
          <button
            aria-current={page === "directory" ? "page" : undefined}
            className={`nav-item ${page === "directory" ? "active" : ""}`}
            onClick={() => setPage("directory")}
          >
            <GitBranch size={17} /> OrgDirectory{" "}
            <span className="nav-chevron">›</span>
          </button>
          <button
            aria-current={page === "insights" ? "page" : undefined}
            className={`nav-item ${page === "insights" ? "active" : ""}`}
            onClick={() => setPage("insights")}
          >
            <Activity size={17} /> Statistics
          </button>
          <div className="nav-label space-top">REFERENCE</div>
          <a className="nav-item" href="./docs/" target="_blank">
            <BookOpen size={17} /> Swagger UI{" "}
            <ArrowUp size={13} className="external" />
          </a>
          <a
            className="nav-item"
            href="./openapi/organization-service.yaml"
            target="_blank"
          >
            <Code2 size={17} /> OpenAPI spec{" "}
            <ArrowUp size={13} className="external" />
          </a>
          <a
            className="nav-item"
            href="./openapi/orgdirectory-service.yaml"
            target="_blank"
          >
            <BookOpen size={17} /> Directory spec{" "}
            <ArrowUp size={13} className="external" />
          </a>
        </nav>
        <a className="sidebar-bottom" href="./docs/" target="_blank">
          <div className="help-icon">
            <CircleHelp size={16} />
          </div>
          <div>
            <strong>Need a hand?</strong>
            <span>Explore the API reference</span>
          </div>
          <ChevronRight size={15} className="muted" />
        </a>
      </aside>

      <main className="main" id="main-content" tabIndex={-1}>
        <header className="topbar">
          <div className="breadcrumbs">
            Workspace <span>/</span>{" "}
            {page === "organizations"
              ? "Organizations"
              : page === "directory"
                ? "OrgDirectory"
                : "Statistics"}
          </div>
          <div className="top-actions">
            <span className="connected">
              <i />{" "}
              {list.isError
                ? "Service unavailable"
                : list.isLoading
                  ? "Connecting..."
                  : "Service connected"}
            </span>
            <a
              className="icon-btn docs-link"
              href="./openapi/organization-service.yaml"
              target="_blank"
              aria-label="Open API specification"
            >
              <BookOpen size={17} />
            </a>
            <span className="avatar">HU</span>
          </div>
        </header>
        <div className="content">
          {notice && (
            <div
              className={`notice ${notice.includes(":") ? "notice-error" : ""}`}
            >
              <span>{notice}</span>
              <button
                aria-label="Dismiss notification"
                onClick={() => setNotice("")}
              >
                <X size={15} />
              </button>
            </div>
          )}
          {page === "organizations" && (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">
                    <span className="eyebrow-icon">
                      <Building2 size={14} />
                    </span>{" "}
                    COLLECTION
                  </div>
                  <h1>Organizations</h1>
                  <p>
                    Manage and explore the organizations in your collection.
                  </p>
                </div>
                <button
                  className="button primary"
                  onClick={() => setEditor("new")}
                >
                  <Plus size={16} /> Add organization
                </button>
              </div>
              <div className="metric-row">
                <Metric
                  icon={<Building2 size={18} />}
                  label="Matching organizations"
                  value={list.data?.total?.toLocaleString() ?? "—"}
                  detail="Across all matching pages"
                />
                <Metric
                  icon={<Gauge size={18} />}
                  label="Current page"
                  value={String(filters.page).padStart(2, "0")}
                  detail={`Showing ${list.data?.items.length ?? 0} entries`}
                />
                <Metric
                  icon={<Activity size={18} />}
                  label="API status"
                  value={
                    list.isError
                      ? "Unavailable"
                      : list.isLoading
                        ? "Checking"
                        : "Operational"
                  }
                  detail="Organization Service"
                  status={!list.isError && !list.isLoading}
                />
              </div>
              <section className="panel">
                <div className="panel-head">
                  <div>
                    <h2>All organizations</h2>
                    <p>A live view of your organization records</p>
                  </div>
                  <button
                    className="icon-btn refresh"
                    aria-label="Refresh"
                    onClick={() => list.refetch()}
                  >
                    <RefreshCw
                      size={16}
                      className={list.isFetching ? "spin" : ""}
                    />
                  </button>
                </div>
                <div className="toolbar">
                  <div className="searchbox">
                    <Search size={16} />
                    <input
                      aria-label="Filter by exact organization name"
                      placeholder="Filter by exact name..."
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      onKeyDown={(e) => e.key === "Enter" && applyFilters()}
                    />
                    <kbd>↵</kbd>
                  </div>
                  <select
                    aria-label="Organization type filter"
                    className="select"
                    value={type}
                    onChange={(e) => {
                      setType(e.target.value);
                      setFilters((f) => ({
                        ...f,
                        type: e.target.value,
                        page: 1,
                      }));
                    }}
                  >
                    <option value="">All types</option>
                    {typeOptions.map((item) => (
                      <option key={item}>{item}</option>
                    ))}
                  </select>
                  <button
                    className="button secondary"
                    aria-expanded={showFilters}
                    aria-controls="organization-filters"
                    onClick={() => setShowFilters((v) => !v)}
                  >
                    <Filter size={15} /> Filters
                  </button>
                  <div className="toolbar-spacer" />
                  <span className="sort-label">Sort by</span>
                  <select
                    aria-label="Sort field"
                    className="select sort-select"
                    value={filters.sortBy}
                    onChange={(e) =>
                      setFilters((f) => ({
                        ...f,
                        sortBy: e.target.value,
                        page: 1,
                      }))
                    }
                  >
                    {fieldOptions.map((field) => (
                      <option key={field} value={field}>
                        {field}
                      </option>
                    ))}
                  </select>
                  <button
                    className="icon-btn sort-dir"
                    title="Toggle sort direction"
                    onClick={() =>
                      setFilters((f) => ({
                        ...f,
                        sortOrder: f.sortOrder === "asc" ? "desc" : "asc",
                        page: 1,
                      }))
                    }
                  >
                    {filters.sortOrder === "asc" ? (
                      <ArrowUp size={15} />
                    ) : (
                      <ArrowDown size={15} />
                    )}
                  </button>
                </div>
                {showFilters && (
                  <form
                    id="organization-filters"
                    className="advanced-filters"
                    onSubmit={(e) => {
                      e.preventDefault();
                      applyFilters();
                    }}
                  >
                    <div className="advanced-grid">
                      {[
                        "id",
                        "coordinates.x",
                        "coordinates.y",
                        "creationDate",
                        "annualTurnover",
                        "fullName",
                        "postalAddress.street",
                      ].map((field) => (
                        <label key={field}>
                          {field}
                          <input
                            type={
                              field === "creationDate"
                                ? "date"
                                : [
                                      "id",
                                      "coordinates.x",
                                      "coordinates.y",
                                      "annualTurnover",
                                    ].includes(field)
                                  ? "number"
                                  : "text"
                            }
                            step={field === "coordinates.x" ? "any" : "1"}
                            value={extraFilters[field] ?? ""}
                            onChange={(e) =>
                              setExtraFilters((v) => ({
                                ...v,
                                [field]: e.target.value,
                              }))
                            }
                            placeholder="Any value"
                          />
                        </label>
                      ))}
                    </div>
                    <div className="filter-actions">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => {
                          setSearch("");
                          setType("");
                          setExtraFilters({});
                          setFilters(initialFilters);
                        }}
                      >
                        Reset filters
                      </Button>
                      <Button type="submit">Apply filters</Button>
                    </div>
                  </form>
                )}
                {list.isError ? (
                  <ErrorState
                    message={list.error.message}
                    retry={() => list.refetch()}
                  />
                ) : (
                  <div className="table-wrap">
                    <table>
                      <thead>
                        {table.getHeaderGroups().map((group) => (
                          <tr key={group.id}>
                            {group.headers.map((header) => (
                              <th key={header.id}>
                                {flexRender(
                                  header.column.columnDef.header,
                                  header.getContext(),
                                )}
                              </th>
                            ))}
                          </tr>
                        ))}
                      </thead>
                      <tbody>
                        {list.isLoading ? (
                          <tr>
                            <td colSpan={7}>
                              <div className="loading">
                                <LoaderCircle className="spin" size={18} />{" "}
                                Loading organizations...
                              </div>
                            </td>
                          </tr>
                        ) : table.getRowModel().rows.length ? (
                          table.getRowModel().rows.map((row) => (
                            <tr key={row.id}>
                              {row.getVisibleCells().map((cell) => (
                                <td key={cell.id}>
                                  {flexRender(
                                    cell.column.columnDef.cell,
                                    cell.getContext(),
                                  )}
                                </td>
                              ))}
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={7}>
                              <div className="empty">
                                <span className="empty-icon">
                                  <Search size={20} />
                                </span>
                                <strong>No organizations found</strong>
                                <span>
                                  Try a different search or add a new
                                  organization.
                                </span>
                              </div>
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
                <div className="table-footer">
                  <span>
                    Showing <strong>{list.data?.items.length ?? 0}</strong> of{" "}
                    <strong>{list.data?.total ?? 0}</strong> organizations
                  </span>
                  <div className="pagination">
                    <select
                      aria-label="Rows per page"
                      className="page-size"
                      value={filters.size}
                      onChange={(e) =>
                        setFilters((f) => ({
                          ...f,
                          size: Number(e.target.value),
                          page: 1,
                        }))
                      }
                    >
                      <option value={10}>10 / page</option>
                      <option value={25}>25 / page</option>
                      <option value={50}>50 / page</option>
                      <option value={100}>100 / page</option>
                    </select>
                    <span>
                      Page <strong>{filters.page}</strong> of{" "}
                      <strong>
                        {Math.max(
                          1,
                          Math.ceil((list.data?.total ?? 0) / filters.size),
                        )}
                      </strong>
                    </span>
                    <button
                      className="icon-btn"
                      aria-label="Previous page"
                      disabled={filters.page <= 1}
                      onClick={() =>
                        setFilters((f) => ({ ...f, page: f.page - 1 }))
                      }
                    >
                      <ChevronLeft size={16} />
                    </button>
                    <button
                      className="icon-btn"
                      aria-label="Next page"
                      disabled={
                        filters.page >=
                        Math.ceil((list.data?.total ?? 0) / filters.size)
                      }
                      onClick={() =>
                        setFilters((f) => ({ ...f, page: f.page + 1 }))
                      }
                    >
                      <ChevronRight size={16} />
                    </button>
                  </div>
                </div>
              </section>
              <div className="footnote">
                <Sparkles size={14} /> Data is fetched live from your
                Organization Service <span>·</span>{" "}
                <span className="mono">GET /organizations</span>
              </div>
            </>
          )}
          {page === "directory" && <DirectoryPage />}
          {page === "insights" && (
            <InsightsPage
              stats={stats.data}
              loading={stats.isLoading}
              error={stats.error?.message}
              retry={() => stats.refetch()}
              threshold={threshold}
              setThreshold={setThreshold}
            />
          )}
        </div>
      </main>
      <EmployeesDialog
        organization={employeeOrg}
        close={() => setEmployeeOrg(null)}
      />
      <Editor
        open={editor !== null}
        initial={editor === "new" ? null : editor}
        close={() => {
          setEditor(null);
          mutation.reset();
        }}
        error={mutation.error?.message}
        save={(value) =>
          mutation.mutate({
            value,
            id: editor && editor !== "new" ? editor.id : undefined,
          })
        }
        loading={mutation.isPending}
      />
    </div>
  );
}

function Metric({
  icon,
  label,
  value,
  detail,
  status,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  detail: string;
  status?: boolean;
}) {
  return (
    <div className="metric">
      <div className="metric-top">
        <span className="metric-icon">{icon}</span>
        {status !== undefined && (
          <span className="status-pill">
            <i /> Live
          </span>
        )}
      </div>
      <span className="metric-label">{label}</span>
      <strong className="metric-value">{value}</strong>
      <span className="metric-detail">{detail}</span>
    </div>
  );
}

function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div className="error-state">
      <span className="error-mark">!</span>
      <strong>Could not load this view</strong>
      <span>{message}</span>
      <button className="button secondary" onClick={retry}>
        <RefreshCw size={14} /> Try again
      </button>
    </div>
  );
}

function DirectoryPage() {
  const [min, setMin] = useState("0");
  const [max, setMax] = useState("10");
  const [field, setField] = useState("name");
  const [desc, setDesc] = useState(false);
  const [mode, setMode] = useState<"filter" | "order">("filter");
  const query = useQuery({
    queryKey: ["directory", mode, Number(min), Number(max), field, desc],
    queryFn: () =>
      mode === "filter"
        ? directoryFilter(Number(min), Number(max))
        : directoryOrder(field, desc),
    enabled: false,
  });
  const columns = useMemo<ColumnDef<Organization>[]>(
    () => [
      {
        accessorKey: "id",
        header: "ID",
        cell: (i) => (
          <span className="mono muted">#{i.getValue<number>()}</span>
        ),
      },
      {
        accessorKey: "name",
        header: "Organization",
        cell: ({ row }) => (
          <div className="org-cell">
            <span className="org-mark">{row.original.name.slice(0, 1)}</span>
            <span className="strong">{row.original.name}</span>
          </div>
        ),
      },
      { accessorKey: "type", header: "Type" },
      { accessorKey: "street", header: "Address" },
      {
        accessorKey: "annualTurnover",
        header: "Turnover",
        cell: (i) => i.getValue<number | null>()?.toLocaleString() ?? "Not set",
      },
    ],
    [],
  );
  const table = useReactTable({
    data: query.data ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">
            <span className="eyebrow-icon">
              <GitBranch size={14} />
            </span>{" "}
            SECONDARY SERVICE
          </div>
          <h1>OrgDirectory</h1>
          <p>Filter and sort records through the directory service.</p>
        </div>
        <a
          className="button secondary"
          href="./openapi/orgdirectory-service.yaml"
          target="_blank"
        >
          <Code2 size={15} /> View spec
        </a>
      </div>
      <div className="directory-grid">
        <section className="panel query-panel">
          <div className="panel-head">
            <div>
              <h2>Build a query</h2>
              <p>Choose one of the directory endpoints</p>
            </div>
            <span className="endpoint-chip">GET</span>
          </div>
          <div className="mode-switch">
            <button
              className={mode === "filter" ? "selected" : ""}
              onClick={() => setMode("filter")}
            >
              <Filter size={15} /> Employee count
            </button>
            <button
              className={mode === "order" ? "selected" : ""}
              onClick={() => setMode("order")}
            >
              <ArrowDownUp size={15} /> Sort organizations
            </button>
          </div>
          {mode === "filter" ? (
            <div className="form-grid">
              <label>
                Minimum employees
                <input
                  type="number"
                  min="0"
                  value={min}
                  onChange={(e) => setMin(e.target.value)}
                />
              </label>
              <label>
                Maximum employees
                <input
                  type="number"
                  min="0"
                  value={max}
                  onChange={(e) => setMax(e.target.value)}
                />
              </label>
            </div>
          ) : (
            <div className="form-grid">
              <label>
                Organization field
                <select
                  value={field}
                  onChange={(e) => setField(e.target.value)}
                >
                  {fieldOptions.map((f) => (
                    <option key={f}>{f}</option>
                  ))}
                </select>
              </label>
              <label>
                Direction
                <select
                  value={String(desc)}
                  onChange={(e) => setDesc(e.target.value === "true")}
                >
                  <option value="false">Ascending</option>
                  <option value="true">Descending</option>
                </select>
              </label>
            </div>
          )}
          <div className="endpoint-preview">
            <span>REQUEST</span>
            <code>
              {mode === "filter"
                ? `/orgdirectory/filter/employees/${min || ":min"}/${max || ":max"}`
                : `/orgdirectory/order/${field}/${desc}`}
            </code>
          </div>
          <button
            className="button primary run-button"
            onClick={() => query.refetch()}
            disabled={
              mode === "filter" && (!min || !max || Number(min) > Number(max))
            }
          >
            <Sparkles size={15} /> Run query
          </button>
        </section>
        <div className="directory-aside">
          <div className="info-card">
            <span className="info-icon">
              <GitBranch size={18} />
            </span>
            <h3>Service composition</h3>
            <p>
              OrgDirectory calls the Organization Service and returns a derived
              view of the same collection.
            </p>
            <div className="flow">
              <span>OrgDirectory</span>
              <ArrowDown size={14} />
              <span>Organization API</span>
            </div>
          </div>
          <div className="info-card endpoint-info">
            <span className="info-icon">
              <CircleHelp size={18} />
            </span>
            <h3>About employee filtering</h3>
            <p>
              The count range is inclusive. Use whole numbers from zero upward.
            </p>
            <span className="tiny-code">
              min-employees-count ≤ count ≤ max-employees-count
            </span>
          </div>
        </div>
      </div>
      {query.isError && (
        <ErrorState
          message={query.error.message}
          retry={() => query.refetch()}
        />
      )}
      {query.data && (
        <section className="panel results-panel">
          <div className="panel-head">
            <div>
              <h2>Query results</h2>
              <p>{query.data.length} matching organizations</p>
            </div>
            <button
              aria-label="Refresh directory results"
              className="icon-btn"
              onClick={() => query.refetch()}
            >
              <RefreshCw size={15} />
            </button>
          </div>
          <ResultsTable table={table} loading={query.isFetching} />
        </section>
      )}
      <div className="footnote">
        <Activity size={14} /> Directory routes are served by the OrgDirectory
        service <span>·</span>
        <span className="mono">/orgdirectory</span>
      </div>
    </>
  );
}

function ResultsTable({
  table,
  loading,
}: {
  table: Table<Organization>;
  loading: boolean;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          {table.getHeaderGroups().map((g) => (
            <tr key={g.id}>
              {g.headers.map((h) => (
                <th key={h.id}>
                  {flexRender(h.column.columnDef.header, h.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={5}>
                <div className="loading">
                  <LoaderCircle size={18} className="spin" /> Loading results...
                </div>
              </td>
            </tr>
          ) : table.getRowModel().rows.length ? (
            table.getRowModel().rows.map((r) => (
              <tr key={r.id}>
                {r.getVisibleCells().map((c) => (
                  <td key={c.id}>
                    {flexRender(c.column.columnDef.cell, c.getContext())}
                  </td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={5}>
                <div className="empty">
                  <strong>No results yet</strong>
                  <span>Run a directory query to see matching records.</span>
                </div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function InsightsPage({
  stats,
  loading,
  error,
  retry,
  threshold,
  setThreshold,
}: {
  stats?: Awaited<ReturnType<typeof getStats>>;
  loading: boolean;
  error?: string;
  retry: () => void;
  threshold: number;
  setThreshold: (v: number) => void;
}) {
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">
            <span className="eyebrow-icon">
              <Activity size={14} />
            </span>{" "}
            COLLECTION METRICS
          </div>
          <h1>Statistics</h1>
          <p>Quick aggregates calculated by the Organization Service.</p>
        </div>
        <button className="button secondary" onClick={retry}>
          <RefreshCw size={15} /> Refresh data
        </button>
      </div>
      <div className="insight-grid">
        <section className="metric insight-stat">
          <span className="metric-icon">
            <Gauge size={18} />
          </span>
          <label className="metric-label threshold-label">
            Turnover below{" "}
            <input
              aria-label="Turnover threshold"
              type="number"
              min="1"
              max="2147483647"
              value={threshold}
              onChange={(e) => {
                const n = Number(e.target.value);
                if (n > 0 && n <= 2147483647) setThreshold(n);
              }}
            />
          </label>
          <strong className="metric-value">
            {loading ? "—" : (stats?.belowThreshold ?? 0)}
          </strong>
          <span className="metric-detail">
            Organizations below the threshold
          </span>
          <div className="stat-route">
            GET <code>/stats/annual-turnover/less-than/{"{value}"}</code>
          </div>
        </section>
        <section className="metric insight-stat">
          <span className="metric-icon">
            <GitBranch size={18} />
          </span>
          <span className="metric-label">Unique turnover values</span>
          <strong className="metric-value">
            {loading ? "—" : (stats?.uniqueTurnovers.length ?? 0)}
          </strong>
          <span className="metric-detail">Null is included when unset</span>
          <div className="value-chips">
            {stats?.uniqueTurnovers.map((v, i) => (
              <span key={`${v}-${i}`} className="value-chip">
                {v == null ? "null" : Number(v).toLocaleString()}
              </span>
            ))}
          </div>
        </section>
        <section className="metric insight-stat grouped-stat">
          <span className="metric-icon">
            <Building2 size={18} />
          </span>
          <span className="metric-label">Organizations by name</span>
          <strong className="metric-value">
            {loading ? "—" : (stats?.groups.length ?? 0)}
            <small> groups</small>
          </strong>
          <span className="metric-detail">Grouped record counts</span>
          <div className="groups-list">
            {stats?.groups.map((group) => (
              <div className="group-line" key={group.name}>
                <span>{group.name || "(empty)"}</span>
                <b>{group.count}</b>
              </div>
            ))}
          </div>
          <div className="stat-route">
            GET <code>/stats/grouped-by-name</code>
          </div>
        </section>
      </div>
      {error && <ErrorState message={error} retry={retry} />}
      <div className="footnote">
        <Sparkles size={14} /> Each card maps to a dedicated statistics endpoint
      </div>
    </>
  );
}

function EmployeesDialog({
  organization,
  close,
}: {
  organization: Organization | null;
  close: () => void;
}) {
  const client = useQueryClient();
  const [name, setName] = useState("");
  const [position, setPosition] = useState("");
  const [salary, setSalary] = useState("");
  const id = organization?.id ?? 0;
  const employees = useQuery({
    queryKey: ["employees", id],
    queryFn: () => getEmployees(id),
    enabled: !!organization,
  });
  const refresh = () =>
    client.invalidateQueries({ queryKey: ["employees", id] });
  const add = useMutation({
    mutationFn: () =>
      addEmployee(id, {
        name: name.trim(),
        position: position || null,
        salary: salary === "" ? null : Number(salary),
      }),
    onSuccess: () => {
      refresh();
      setName("");
      setPosition("");
      setSalary("");
    },
  });
  const remove = useMutation({
    mutationFn: (employeeId: number) => deleteEmployee(id, employeeId),
    onSuccess: refresh,
  });
  return (
    <Dialog.Root
      open={!!organization}
      onOpenChange={(open) => !open && close()}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay" />
        <Dialog.Content className="dialog-content">
          <div className="dialog-title-row">
            <div>
              <Dialog.Title>Employees</Dialog.Title>
              <Dialog.Description>
                Manage the employees of {organization?.name}.
              </Dialog.Description>
            </div>
            <Dialog.Close
              className="icon-btn"
              aria-label="Close employees dialog"
            >
              <X size={17} />
            </Dialog.Close>
          </div>
          {employees.isError && (
            <div className="form-error">{employees.error.message}</div>
          )}
          <div className="employee-list">
            {employees.isLoading ? (
              <div className="loading">Loading employees…</div>
            ) : employees.data?.length ? (
              employees.data.map((employee) => (
                <div className="employee-row" key={employee.id}>
                  <div>
                    <strong>{employee.name}</strong>
                    <span>
                      {employee.position || "No position"} ·{" "}
                      {employee.salary == null
                        ? "No salary"
                        : employee.salary.toLocaleString()}
                    </span>
                  </div>
                  <button
                    className="icon-btn danger"
                    disabled={remove.isPending}
                    aria-label={`Delete ${employee.name}`}
                    onClick={() => {
                      if (window.confirm(`Delete employee ${employee.name}?`))
                        remove.mutate(employee.id);
                    }}
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              ))
            ) : (
              <div className="empty">
                <strong>No employees yet</strong>
                <span>Add the first employee below.</span>
              </div>
            )}
          </div>
          <form
            className="editor-form"
            onSubmit={(event) => {
              event.preventDefault();
              add.mutate();
            }}
          >
            <label>
              Employee name
              <input
                required
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <div className="form-grid">
              <label>
                Position
                <input
                  value={position}
                  onChange={(event) => setPosition(event.target.value)}
                  placeholder="Optional"
                />
              </label>
              <label>
                Salary
                <input
                  type="number"
                  min="0"
                  step="any"
                  value={salary}
                  onChange={(event) => setSalary(event.target.value)}
                  placeholder="Optional"
                />
              </label>
            </div>
            {(add.error || remove.error) && (
              <div className="form-error">
                {(add.error || remove.error)?.message}
              </div>
            )}
            <div className="dialog-actions">
              <Button type="submit" disabled={add.isPending || !name.trim()}>
                <Plus size={15} /> Add employee
              </Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function Editor({
  open,
  initial,
  close,
  save,
  loading,
  error,
}: {
  open: boolean;
  initial: Organization | null;
  close: () => void;
  save: (value: OrganizationInput) => void;
  loading: boolean;
  error?: string;
}) {
  const [validation, setValidation] = useState("");
  // Remounting on the open/record transition resets the editor to the selected row.
  return (
    <Dialog.Root open={open} onOpenChange={(value) => !value && close()}>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay" />
        <Dialog.Content className="dialog-content">
          <div className="dialog-title-row">
            <div>
              <Dialog.Title>
                {initial ? "Edit organization" : "Add organization"}
              </Dialog.Title>
              <Dialog.Description>
                Enter the organization details below. ID and creation date are
                generated by the service.
              </Dialog.Description>
            </div>
            <Dialog.Close
              className="icon-btn"
              aria-label="Close organization dialog"
            >
              <X size={17} />
            </Dialog.Close>
          </div>
          {error && (
            <div className="form-error" role="alert">
              {error}
            </div>
          )}
          <OrganizationForm
            key={`${open}-${initial?.id ?? "new"}`}
            initial={initial}
            onSubmit={save}
            loading={loading}
            onValidation={setValidation}
            validation={validation}
            onCancel={close}
          />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

const emptyForm: OrganizationInput = {
  name: "",
  x: 0,
  y: 0,
  annualTurnover: null,
  fullName: null,
  type: "COMMERCIAL",
  street: "",
};
function OrganizationForm({
  initial,
  onSubmit,
  loading,
  onValidation,
  validation,
  onCancel,
}: {
  initial: Organization | null;
  onSubmit: (v: OrganizationInput) => void;
  loading: boolean;
  onValidation: (v: string) => void;
  validation: string;
  onCancel: () => void;
}) {
  const [form, setForm] = useState<OrganizationInput>(
    initial
      ? {
          name: initial.name,
          x: initial.x,
          y: initial.y,
          annualTurnover: initial.annualTurnover,
          fullName: initial.fullName,
          type: initial.type,
          street: initial.street,
        }
      : emptyForm,
  );
  const update = (key: keyof OrganizationInput, value: string) =>
    setForm((v) => ({
      ...v,
      [key]: ["x", "y", "annualTurnover"].includes(key)
        ? value === "" && key === "annualTurnover"
          ? null
          : Number(value)
        : value,
    }));
  return (
    <form
      className="editor-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (!form.name.trim()) {
          onValidation("Name is required.");
          return;
        }
        if (form.street.length > 158) {
          onValidation("Street must be at most 158 characters.");
          return;
        }
        if (form.annualTurnover !== null && form.annualTurnover <= 0) {
          onValidation("Annual turnover must be greater than zero.");
          return;
        }
        onValidation("");
        onSubmit({
          ...form,
          name: form.name.trim(),
          fullName: form.fullName || null,
          street: form.street.trim(),
        });
      }}
    >
      <label>
        Organization name <span>*</span>
        <input
          required
          value={form.name}
          onChange={(e) => update("name", e.target.value)}
          placeholder="e.g. Acme"
        />
      </label>
      <label>
        Full legal name
        <input
          value={form.fullName ?? ""}
          onChange={(e) => update("fullName", e.target.value)}
          placeholder="Optional"
        />
      </label>
      <div className="form-grid">
        <label>
          Coordinate X
          <input
            type="number"
            step="any"
            value={form.x}
            onChange={(e) => update("x", e.target.value)}
          />
        </label>
        <label>
          Coordinate Y
          <input
            type="number"
            step="1"
            value={form.y}
            onChange={(e) => update("y", e.target.value)}
          />
        </label>
      </div>
      <div className="form-grid">
        <label>
          Annual turnover
          <input
            type="number"
            min="1"
            value={form.annualTurnover ?? ""}
            onChange={(e) => update("annualTurnover", e.target.value)}
            placeholder="Optional"
          />
        </label>
        <label>
          Organization type
          <select
            value={form.type}
            onChange={(e) => update("type", e.target.value)}
          >
            {typeOptions.map((v) => (
              <option key={v}>{v}</option>
            ))}
          </select>
        </label>
      </div>
      <label>
        Street address <span>*</span>
        <input
          maxLength={158}
          value={form.street}
          onChange={(e) => update("street", e.target.value)}
          placeholder="Street address"
        />
      </label>
      {validation && <div className="form-error">{validation}</div>}
      <div className="dialog-actions">
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button disabled={loading}>
          {loading ? (
            <LoaderCircle size={15} className="spin" />
          ) : (
            <Check size={15} />
          )}
          {initial ? "Save changes" : "Create organization"}
        </Button>
      </div>
    </form>
  );
}

export default App;
